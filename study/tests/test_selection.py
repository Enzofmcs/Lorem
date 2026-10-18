import json
from pathlib import Path
import tempfile
import unittest
import urllib.parse

from lorem_study.api import ApiClient
from lorem_study.collect import collect
from lorem_study.config import load_config
from lorem_study.fixture import fixture_api
from lorem_study.io import StudyError, canonical, read_json
from lorem_study.selection import POLICY, regular_round


class SelectionTests(unittest.TestCase):
    def test_regular_rounds_only_and_special_names_excluded(self):
        for name in ("Codeforces Round 900 (Div. 2)", "Codeforces Global Round 20", "CodeTON Round 5 (Div. 1 + Div. 2)"):
            self.assertTrue(regular_round({"type": "CF", "name": name}))
        for meta in ({"type": "IOI", "name": "Codeforces Round 1 (Div. 2)"},
                     {"type": "CF", "name": "April Fools Day Contest 2024"},
                     {"type": "CF", "name": "Marathon (Div. 2)"},
                     {"type": "CF", "name": "Unknown invitation"}, {}):
            self.assertFalse(regular_round(meta))

    def test_structural_screening_freezes_before_status_and_replays_offline(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            config = {**load_config(), "selection_policy": POLICY, "max_screened_candidates": 30}
            base = fixture_api(4)
            calls = []
            def transport(url, timeout):
                method = urllib.parse.urlsplit(url).path.rsplit("/", 1)[-1]
                calls.append(method)
                status, headers, body = base(url, timeout)
                data = json.loads(body)
                if method == "contest.standings" and data["result"]["contest"]["id"] == 900000:
                    data["result"]["problems"][1]["index"] = "B1"
                if method == "contest.status":
                    self.assertTrue((root / "selection.json").exists())
                    self.assertEqual(len(read_json(root / "selection.json")["contest_ids"]), 2)
                return status, headers, canonical(data)
            client = ApiClient(root, config, transport=transport, sleep=lambda _: None, data_kind="synthetic")
            result = collect(root, config, pilot=True, client=client)
            self.assertEqual(result["complete"], 2)
            ids = read_json(root / "selection.json")["contest_ids"]
            self.assertNotIn(900000, ids)
            self.assertEqual(len(ids), 2)
            replay = collect(root, config, pilot=True, offline=True,
                             client=ApiClient(root, config, offline=True, data_kind="synthetic"))
            self.assertEqual(result["snapshot_sha256"], replay["snapshot_sha256"])
            self.assertEqual(replay["api_calls_this_run"], 0)

    def test_screening_failure_is_not_replaced_by_next_candidate(self):
        with tempfile.TemporaryDirectory() as directory:
            config = {**load_config(), "selection_policy": POLICY}
            base = fixture_api(4)
            calls = []
            def transport(url, timeout):
                calls.append(url)
                if "contest.standings" in url:
                    return 403, {}, b"denied"
                return base(url, timeout)
            with self.assertRaises(StudyError):
                collect(directory, config, pilot=True,
                        client=ApiClient(directory, config, transport=transport, sleep=lambda _: None))
            self.assertFalse((Path(directory) / "selection.json").exists())
            self.assertEqual(sum("contest.standings" in url for url in calls), 1)
