import json
from pathlib import Path
import tempfile
import unittest
import urllib.parse

from lorem_study.api import ApiClient
from lorem_study.collect import collect, select_contests
from lorem_study.config import load_config
from lorem_study.fixture import fixture_api
from lorem_study.io import StudyError, canonical, raw_json, read_json


class FakeClock:
    def __init__(self):
        self.value, self.waits = 0.0, []

    def sleep(self, seconds):
        self.waits.append(seconds)
        self.value += seconds

    def now(self):
        return self.value


class ApiTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.config = load_config()
        self.clock = FakeClock()

    def client(self, transport, **kw):
        return ApiClient(self.root, self.config, transport=transport, monotonic=self.clock.now,
                         sleep=self.clock.sleep, now=lambda: "2026-10-18T00:00:00Z", **kw)

    def test_global_gate_cache_offline_hash_and_immutable_versions(self):
        calls = []

        def transport(url, timeout):
            calls.append(self.clock.now())
            return 200, {}, canonical({"status": "OK", "result": [len(calls)]})
        client = self.client(transport)
        _, first = client.request("contest.list")
        _, second = client.request("contest.list", refresh=True)
        self.assertEqual(calls, [0, 2])
        self.assertNotEqual(first["sha256"], second["sha256"])
        self.assertEqual(raw_json(self.root, first), [1])
        self.assertEqual(self.client(lambda *_: self.fail("Offline used network"), offline=True).request("contest.list")[0], [2])
        path = self.root / "raw" / (second["sha256"] + ".json")
        path.write_text("{}")
        with self.assertRaisesRegex(StudyError, "checksum"):
            self.client(transport, offline=True).request("contest.list")

    def test_retry_after_and_network_backoff(self):
        calls = []

        def transport(url, timeout):
            calls.append(self.clock.now())
            if len(calls) == 1:
                raise OSError("temporary failure")
            if len(calls) == 2:
                return 429, {"Retry-After": "7"}, canonical({"status": "FAILED", "comment": "limit"})
            return 200, {}, canonical({"status": "OK", "result": []})
        self.client(transport).request("contest.list")
        self.assertEqual(calls, [0, 2, 9])

    def test_failed_is_not_empty_success_or_cached(self):
        client = self.client(lambda *_: (200, {}, canonical({"status": "FAILED", "comment": "unavailable"})))
        with self.assertRaisesRegex(StudyError, "unavailable"):
            client.request("contest.ratingChanges", {"contestId": 123})
        self.assertFalse(client.index)
        self.assertEqual(len(list((self.root / "raw").glob("*.json"))), 1)
        with self.assertRaisesRegex(StudyError, "cache miss"):
            self.client(lambda *_: self.fail(), offline=True).request("contest.list")

    def test_missing_page_never_completes_then_resumes_using_cached_pages(self):
        self.config["page_size"] = 3
        transport = fixture_api(2)

        def broken(url, timeout):
            q = dict(urllib.parse.parse_qsl(urllib.parse.urlsplit(url).query))
            if "contest.status" in url and q["from"] == "4":
                return 403, {}, b"denied"
            return transport(url, timeout)
        failed = collect(self.root, self.config, client=self.client(broken))
        self.assertEqual(failed["complete"], 0)
        first_hashes = set((self.root / "raw").glob("*.json"))
        requested = []

        def resumed(url, timeout):
            requested.append(url)
            return transport(url, timeout)
        result = collect(self.root, self.config, client=self.client(resumed))
        self.assertEqual(result["complete"], 2)
        self.assertTrue(first_hashes.issubset(set((self.root / "raw").glob("*.json"))))
        self.assertFalse(any("contest.status" in u and "from=1&" in u for u in requested))
        self.assertFalse(any("contest.standings" in u for u in requested))
        again = collect(self.root, self.config, offline=True, client=self.client(lambda *_: self.fail(), offline=True))
        self.assertEqual(again["network_requests_this_run"], 0)
        self.assertEqual(result["snapshot_sha256"], again["snapshot_sha256"])

    def test_repeated_full_page_is_incomplete(self):
        self.config["page_size"] = 2
        base = fixture_api(2)

        def transport(url, timeout):
            parts = urllib.parse.urlsplit(url)
            params = dict(urllib.parse.parse_qsl(parts.query))
            if "contest.status" in url:
                params["from"] = "1"
                url = urllib.parse.urlunsplit(parts._replace(query=urllib.parse.urlencode(params)))
            return base(url, timeout)
        result = collect(self.root, self.config, client=self.client(transport))
        self.assertEqual(result["complete"], 0)
        errors = [e["error"] for e in read_json(self.root / "snapshot.json")["contests"].values()]
        self.assertTrue(all("Repeated full page" in e for e in errors))

    def test_full_official_standings_have_no_pagination_parameters_or_size_cap(self):
        self.config["page_size"] = 3
        base = fixture_api(2)
        standings_calls = []

        def transport(url, timeout):
            if "contest.standings" in url:
                params = dict(urllib.parse.parse_qsl(urllib.parse.urlsplit(url).query))
                self.assertEqual(set(params), {"contestId"})
                standings_calls.append(params)
            return base(url, timeout)
        result = collect(self.root, self.config, client=self.client(transport))
        self.assertEqual(result["complete"], 2)
        self.assertEqual(len(standings_calls), 2)
        for entry in read_json(self.root / "snapshot.json")["contests"].values():
            self.assertEqual(len(entry["standings_refs"]), 1)
            rows = raw_json(self.root, entry["standings_refs"][0])["rows"]
            self.assertEqual(len(rows), 5)  # More than page_size, still one full response.
        offline = collect(self.root, self.config, client=self.client(lambda *_: self.fail(), offline=True))
        self.assertEqual(result["snapshot_sha256"], offline["snapshot_sha256"])

    def test_malformed_full_standings_never_complete(self):
        base = fixture_api(2)
        for bad in ({"rows": []}, {"contest": {"id": -1}, "problems": [], "rows": []},
                    {"contest": {"id": 900000}, "problems": [], "rows": None}):
            def transport(url, timeout):
                if "contest.standings" in url:
                    return 200, {}, canonical({"status": "OK", "result": bad})
                return base(url, timeout)
            with self.subTest(bad=bad), tempfile.TemporaryDirectory() as directory:
                client = ApiClient(directory, self.config, transport=transport,
                                   monotonic=self.clock.now, sleep=self.clock.sleep)
                result = collect(directory, self.config, client=client)
                self.assertEqual(result["complete"], 0)
                errors = [e["error"] for e in read_json(Path(directory) / "snapshot.json")["contests"].values()]
                self.assertTrue(all("Malformed full standings" in e for e in errors))

    def test_pending_verdict_requeried_and_previous_raw_preserved(self):
        base = fixture_api(2)
        statuses = {}

        def transport(url, timeout):
            status, headers, body = base(url, timeout)
            if "contest.status" in url:
                q = dict(urllib.parse.parse_qsl(urllib.parse.urlsplit(url).query))
                cid = q["contestId"]
                statuses[cid] = statuses.get(cid, 0) + 1
                if statuses[cid] == 1:
                    data = json.loads(body)
                    data["result"][0]["verdict"] = "TESTING"
                    body = canonical(data)
            return status, headers, body
        result = collect(self.root, self.config, client=self.client(transport))
        self.assertEqual(result["complete"], 2)
        self.assertEqual(list(statuses.values()), [2, 2])
        self.assertTrue(any(b'"TESTING"' in p.read_bytes() for p in (self.root / "raw").glob("*.json")))

    def test_date_cutoff_not_contest_id(self):
        items = [{"id": 1, "startTimeSeconds": 1593561600, "durationSeconds": 7200, "phase": "FINISHED", "type": "CF"},
                 {"id": 99999999, "startTimeSeconds": 1593561599, "durationSeconds": 7200, "phase": "FINISHED", "type": "CF"},
                 {"id": 2, "durationSeconds": 7200, "phase": "FINISHED", "type": "CF"}]
        self.assertEqual(select_contests(items, self.config, 20), [1])

    def test_non_cf_special_formats_are_not_selected(self):
        common = {"startTimeSeconds": 1593561600, "durationSeconds": 7200,
                  "phase": "FINISHED"}
        items = [{**common, "id": 1, "type": "CF"},
                 {**common, "id": 2, "type": "ICPC"},
                 {**common, "id": 3, "type": "IOI"},
                 {**common, "id": 4}]
        self.assertEqual(select_contests(items, self.config, 20), [1])

    def test_malformed_response_and_long_retry_after_fail_explicitly(self):
        for response in ((200, {}, b"[]"), (429, {"Retry-After": "1000"}, b"{}")):
            with self.subTest(response=response), self.assertRaises(StudyError):
                self.client(lambda *_: response).request("contest.list")
        self.assertEqual(self.clock.value, 0)

    def test_retry_after_http_date_and_cross_kind_cache(self):
        calls = []

        def transport(*args):
            calls.append(self.clock.value)
            if len(calls) == 1:
                return 429, {"Retry-After": "Sun, 18 Oct 2026 00:00:09 GMT"}, b"{}"
            return 200, {}, canonical({"status": "OK", "result": []})
        self.client(transport).request("contest.list")
        self.assertEqual(calls, [0, 9])
        with self.assertRaisesRegex(StudyError, "Cannot mix"):
            self.client(transport, data_kind="synthetic", offline=True).request("contest.list")


if __name__ == "__main__":
    unittest.main()
