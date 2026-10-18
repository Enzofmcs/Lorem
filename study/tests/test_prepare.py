import json
from pathlib import Path
import tempfile
import unittest

from lorem_study.api import ApiClient
from lorem_study.collect import collect
from lorem_study.config import load_config
from lorem_study.fixture import fixture_api
from lorem_study.io import canonical, read_rows
from lorem_study.prepare import _intervals, prepare


def submission(index, seconds, verdict="OK", sid=1):
    return {"id": sid, "problem": {"index": index}, "creationTimeSeconds": seconds, "verdict": verdict}


class IntervalTests(unittest.TestCase):
    def test_textbook_example_and_first_final_ac(self):
        rows = [submission("A", 300, "WRONG_ANSWER"), submission("A", 600),
                submission("B", 1680), submission("C", 3900)]
        result = _intervals(rows, list("ABC"), 0, 7200)
        self.assertEqual([result[i]["duration_seconds"] / 60 for i in "ABC"], [10, 18, 37])
        self.assertEqual([result[i]["event"] for i in "ABC"], [1, 1, 1])

    def test_censoring_and_no_fabricated_unattempted_rows(self):
        result = _intervals([submission("A", 600), submission("B", 1200, "WRONG_ANSWER")], list("ABC"), 0, 7200)
        self.assertEqual(result["B"]["duration_seconds"], 6600)
        self.assertEqual(result["B"]["event"], 0)
        self.assertEqual(result["B"]["sensitivity_last_submission_seconds"], 600)
        self.assertNotIn("C", result)
        self.assertEqual(_intervals([], list("ABC"), 0, 7200), {})

    def test_jump_and_interleaving_do_not_become_negative_times(self):
        for rows in ([submission("B", 600)],
                     [submission("B", 600), submission("A", 1200)],
                     [submission("A", 600, "WRONG_ANSWER"), submission("B", 700, "WRONG_ANSWER"), submission("A", 900), submission("B", 1200)]):
            with self.subTest(rows=rows):
                result = _intervals(rows, list("ABC"), 0, 7200)
                self.assertTrue(all(r.get("reason") for r in result.values()))
                self.assertTrue(all(r.get("duration_seconds") is None for r in result.values()))

    def test_later_return_preserves_prior_prefix_only(self):
        rows = [submission("A", 600), submission("B", 1200), submission("C", 1400, "WRONG_ANSWER"),
                submission("A", 1500, "WRONG_ANSWER"), submission("C", 1800)]
        result = _intervals(rows, list("ABC"), 0, 7200)
        self.assertIsNone(result["A"]["reason"])
        self.assertIsNone(result["B"]["reason"])
        self.assertEqual(result["C"]["reason"], "interleaved_or_skipped_problem")

    def test_zero_and_tied_acs_are_not_silently_corrected(self):
        zero = _intervals([submission("A", 0)], ["A"], 0, 7200)
        self.assertIsNotNone(zero["A"]["reason"])
        tied = _intervals([submission("A", 600), submission("B", 600)], list("AB"), 0, 7200)
        self.assertTrue(all(r["reason"] for r in tied.values()))


class PreparationTests(unittest.TestCase):
    def run_fixture(self, modify=lambda method, data: None):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        config = load_config()
        base = fixture_api(2)

        def transport(url, timeout):
            status, headers, body = base(url, timeout)
            data = json.loads(body)
            method = url.split("?")[0].rsplit("/", 1)[-1]
            modify(method, data["result"])
            return status, headers, canonical(data)
        clock = [0]
        client = ApiClient(root, config, transport=transport, sleep=lambda s: clock.__setitem__(0, clock[0] + s),
                           monotonic=lambda: clock[0], now=lambda: "2020-07-01T00:00:00Z", data_kind="synthetic")
        collect(root, config, client=client)
        report = prepare(root)
        return root, report, list(read_rows(root / "prepared" / "features.jsonl")), list(read_rows(root / "prepared" / "labels.jsonl"))

    def test_full_funnel_features_and_audit_reconcile(self):
        root, report, features, labels = self.run_fixture()
        self.assertEqual(report["observed_attempts"], 18)
        self.assertEqual((report["valid_events"], report["valid_censored"], report["excluded"]), (8, 2, 8))
        self.assertEqual(sum(report["reasons"].values()), report["excluded"])
        self.assertEqual(report["observed_attempts"], report["valid_events"] + report["valid_censored"] + report["excluded"])
        for key in ("by_participant_rating_band_400", "by_problem_rating_band_400", "by_delta_band_400"):
            self.assertEqual(sum(b["observed"] for b in report[key].values()), report["observed_attempts"])
        for f in features:
            self.assertEqual(f["delta"], f["problem_rating"] - f["old_rating"])
            self.assertFalse({"newRating", "rank", "event", "duration_seconds", "first_ac_seconds"} & f.keys())
            self.assertNotIn("synthetic_", json.dumps(f))
        audit = list(read_rows(root / "prepared" / "audit.jsonl"))
        self.assertEqual(len(audit), len(labels))
        self.assertTrue(all(a["rating_changes_ref"]["sha256"] for a in audit))

    def test_missing_conflicting_rating_and_novices_are_quarantined(self):
        def modify(method, data):
            if method == "contest.ratingChanges":
                data[:] = [r for r in data if r["handle"] != "synthetic_sequential"]
                conflict = next(r.copy() for r in data if r["handle"] == "synthetic_without_ac")
                conflict["oldRating"] = 1700
                data.append(conflict)
        _, report, _, labels = self.run_fixture(modify)
        self.assertGreater(report["reasons"]["missing_old_rating"], 0)
        self.assertGreater(report["reasons"]["conflicting_rating_changes"], 0)
        self.assertGreater(report["reasons"]["insufficient_prior_rating_history"], 0)
        self.assertTrue(all(r["duration_seconds"] is None for r in labels if r["quality"] != "valid"))

    def test_upsolve_boundary_virtual_and_team_do_not_change_labels(self):
        def modify(method, data):
            if method != "contest.status":
                return
            seq = next(s for s in data if s["author"]["members"][0]["handle"] == "synthetic_without_ac" and s["problem"]["index"] == "B")
            for n, offset in enumerate((-1, 7200, 7201)):
                extra = json.loads(json.dumps(seq))
                extra.update(id=extra["id"] + 1000 + n, verdict="OK", creationTimeSeconds=1593561600 + (seq["contestId"] - 900000) * 86400 + offset)
                data.append(extra)
            for n, kind in enumerate(("VIRTUAL", "PRACTICE", "OUT_OF_COMPETITION")):
                extra = json.loads(json.dumps(seq))
                extra["id"] += 2000 + n
                extra["author"]["participantType"] = kind
                data.append(extra)
            extra = json.loads(json.dumps(seq))
            extra["id"] += 3000
            extra["author"]["teamId"] = 12
            data.append(extra)
        _, report, _, _ = self.run_fixture(modify)
        self.assertEqual((report["valid_events"], report["valid_censored"]), (8, 2))
        self.assertEqual(report["submission_exclusions"]["outside_contest_window"], 6)
        self.assertEqual(report["submission_exclusions"]["not_official_individual"], 8)

    def test_unresolved_verdict_not_administrative_censor(self):
        def modify(method, data):
            if method == "contest.status":
                data[0]["verdict"] = "TESTING"
        _, report, _, labels = self.run_fixture(modify)
        self.assertEqual(report["valid_censored"], 0)
        self.assertEqual(report["complete_contest_ids"], [])
        self.assertTrue(all(r["reason"] == "incomplete_collection" for r in labels))

    def test_duplicate_submissions_dedup_and_conflicts_excluded(self):
        def duplicate(method, data):
            if method == "contest.status":
                data.append(data[-1].copy())
        _, report, _, _ = self.run_fixture(duplicate)
        self.assertEqual(report["valid_events"], 8)
        self.assertEqual(sum(c["duplicate_submissions"] for c in report["contests"]), 2)

        def conflict(method, data):
            if method == "contest.status":
                extra = json.loads(json.dumps(data[-1]))
                extra["verdict"] = "OK"
                data.append(extra)
        _, report, _, _ = self.run_fixture(conflict)
        self.assertGreater(report["reasons"]["conflicting_submission_identity"], 0)

    def test_missing_and_old_origin_dates_are_ineligible_without_id_inference(self):
        for old_date in (False, True):
            def modify(method, data):
                if method == "contest.list" and old_date:
                    data.append({"id": 123, "name": "Synthetic old origin", "type": "CF", "phase": "FINISHED",
                                 "startTimeSeconds": 1593561599, "durationSeconds": 7200})
                if method == "contest.standings":
                    data["problems"][1]["contestId"] = 123
                if method == "contest.status":
                    for s in data:
                        if s["problem"]["index"] == "B":
                            s["problem"]["contestId"] = 123
                if method == "problemset.problems":
                    for p in data["problems"]:
                        if p["index"] == "B":
                            p["contestId"] = 123
            with self.subTest(old_date=old_date):
                _, report, _, labels = self.run_fixture(modify)
                self.assertGreater(report["reasons"]["origin_date_missing_or_before_cutoff"], 0)
                self.assertTrue(all(r["duration_seconds"] is None for r in labels if r["reason"] == "origin_date_missing_or_before_cutoff"))

    def test_ambiguous_indices_and_inconsistent_contest_dates_not_usable(self):
        def indices(method, data):
            if method == "contest.standings":
                data["problems"][0]["index"] = "A1"
            elif method == "contest.status":
                for s in data:
                    if s["problem"]["index"] == "A":
                        s["problem"]["index"] = "A1"
        _, report, _, labels = self.run_fixture(indices)
        self.assertEqual(report["complete_contest_ids"], [])
        self.assertTrue(all(r["reason"] in ("unsupported_problem_order", "insufficient_prior_rating_history") for r in labels))

        def dates(method, data):
            if method == "contest.standings":
                data["contest"]["startTimeSeconds"] += 1
        _, report, _, labels = self.run_fixture(dates)
        self.assertEqual(report["complete_contest_ids"], [])
        self.assertTrue(all(r["reason"] == "incomplete_collection" for r in labels))

    def test_easy_hard_pair_excludes_entire_contest_including_simple_prefix(self):
        def variants(method, data):
            if method == "contest.standings":
                easy = {**data["problems"][-1], "index": "C1"}
                hard = {**data["problems"][-1], "index": "C2"}
                data["problems"][-1:] = [easy, hard]
            elif method == "contest.status":
                for row in data:
                    if row["problem"]["index"] == "C":
                        row["problem"]["index"] = "C1"
        _, report, features, labels = self.run_fixture(variants)
        self.assertEqual(report["complete_contest_ids"], [])
        self.assertEqual((report["valid_events"], report["valid_censored"]), (0, 0))
        self.assertEqual(report["excluded"], report["observed_attempts"])
        label_by_id = {row["sample_id"]: row for row in labels}
        prefix = [label_by_id[row["sample_id"]] for row in features
                  if row["problem_index"] in ("A", "B") and row["old_rating"] > 0]
        self.assertTrue(prefix)
        self.assertTrue(all(row["reason"] == "unsupported_problem_order" for row in prefix))
        self.assertTrue(all(row["duration_seconds"] is None and row["event"] is None for row in labels))

    def test_missing_problem_rating_is_not_imputed_from_results(self):
        def modify(method, data):
            if method == "problemset.problems":
                for p in data["problems"]:
                    p.pop("rating", None)
        _, report, features, _ = self.run_fixture(modify)
        self.assertEqual(report["complete_contest_ids"], [])
        self.assertGreater(report["reasons"]["missing_problem_rating"], 0)
        self.assertTrue(all(f["problem_rating"] is None and f["delta"] is None for f in features))


if __name__ == "__main__":
    unittest.main()
