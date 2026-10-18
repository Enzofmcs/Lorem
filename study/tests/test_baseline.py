import math
from pathlib import Path
import tempfile
import unittest

from lorem_study.baseline import expected_d012_minutes, km_fit, km_probability, km_quantile, predict, split, train_baseline
from lorem_study.config import load_config
from lorem_study.fixture import create_fixture
from lorem_study.io import StudyError, digest, read_json, read_rows, write_json, write_rows
from lorem_study.prepare import prepare


class KaplanMeierTests(unittest.TestCase):
    def test_hand_computed_risk_sets_ties_and_quantiles(self):
        curve = km_fit([(10, 1), (20, 1), (20, 0), (30, 0)])
        self.assertEqual([r["at_risk"] for r in curve["table"]], [4, 3, 1])
        self.assertAlmostEqual(km_probability(curve, 10), .25)
        self.assertAlmostEqual(km_probability(curve, 20), .5)
        self.assertAlmostEqual(km_probability(curve, 30), .5)
        self.assertIsNone(km_probability(curve, 31))
        self.assertEqual(km_quantile(curve, .5), 20 / 60)
        self.assertIsNone(km_quantile(curve, .9))
        reverse = km_fit([(10, 1), (20, 1), (20, 0), (30, 0)], reverse=True)
        self.assertAlmostEqual(reverse["table"][1]["survival"], .5)

    def test_all_censored_and_all_events_have_explicit_tail_support(self):
        curve = km_fit([(10, 0), (20, 0)])
        self.assertEqual(km_probability(curve, 20), 0)
        self.assertIsNone(km_probability(curve, 21))
        self.assertIsNone(km_quantile(curve, .5))
        all_events = km_fit([(10, 1), (20, 1)])
        probabilities = [km_probability(all_events, t) for t in range(100)]
        self.assertEqual(probabilities, sorted(probabilities))
        self.assertEqual(probabilities[-1], 1)

    def test_invalid_observations_fail_instead_of_fabricating_time(self):
        for observations in ([], [(0, 1)], [(-1, 0)], [(math.inf, 1)], [(math.nan, 1)], [(10, 2)]):
            with self.subTest(observations=observations), self.assertRaises(StudyError):
                km_fit(observations)

    def test_d012_matches_android_time_contract(self):
        for user, problem, expected in ((1200, 1200, 60), (1200, 1600, 100), (1200, 800, 30),
                                        (800, 3000, 120), (1200, 1201, 60.1)):
            self.assertEqual(expected_d012_minutes(user, problem), expected)


class PartitionAndFitTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "study"
        self.config = load_config()
        create_fixture(self.root, self.config)
        self.prepared = prepare(self.root)

    def save_prepared(self):
        write_json(self.root / "prepared" / "manifest.json", self.prepared)

    def test_split_deterministic_minimum_and_shared_origin_bridge(self):
        self.prepared["origin_links"] += [
            {"contest_ids": [900000, 999999], "source": "verified fixture bridge"},
            {"contest_ids": [900001, 999999], "source": "verified fixture bridge"}]
        self.save_prepared()
        result = split(self.root)
        self.assertEqual(result, split(self.root))
        p = result["partitions"]
        self.assertGreaterEqual(len(p["train"]), 6)
        self.assertGreaterEqual(len(p["validation"]), 2)
        self.assertGreaterEqual(len(p["test"]), 2)
        self.assertEqual(sum(map(len, p.values())), 10)
        self.assertFalse(set(p["train"]) & set(p["test"]))
        self.assertTrue(any({900000, 900001} <= set(ids) for ids in p.values()))

    def test_too_few_or_unpartitionable_groups_are_pilot_only(self):
        self.prepared["complete_contest_ids"] = self.prepared["complete_contest_ids"][:2]
        self.save_prepared()
        with self.assertRaisesRegex(StudyError, "pilot only"):
            split(self.root)
        self.prepared["complete_contest_ids"] = list(range(900000, 900010))
        self.prepared["origin_links"] = [{"contest_ids": list(range(900000, 900010)), "source": "all shared"}]
        self.save_prepared()
        with self.assertRaisesRegex(StudyError, "Shared groups"):
            split(self.root)

    def test_baseline_and_censoring_use_only_training_and_no_outcome_exports(self):
        parts = split(self.root)
        first = train_baseline(self.root)
        self.assertEqual(first["global"]["n"], 30)
        self.assertEqual(first["global"]["events"], 24)
        self.assertEqual(first["delta_bins"], {})
        predictions = list(read_rows(self.root / "baseline" / "predictions.jsonl"))
        self.assertEqual({r["partition"] for r in predictions}, {"train", "validation", "test"})
        self.assertTrue(all(not ({"event", "duration_seconds", "rank"} & r.keys()) for r in predictions))
        # Change only held-out features/outcomes and update dataset provenance, keeping the frozen assignment.
        features = list(read_rows(self.root / "prepared" / "features.jsonl"))
        labels = list(read_rows(self.root / "prepared" / "labels.jsonl"))
        for f in features:
            if f["contest_id"] not in parts["partitions"]["train"]:
                f["old_rating"] = 9990
                f["delta"] = f["problem_rating"] - f["old_rating"]
        for r in labels:
            if r["contest_id"] not in parts["partitions"]["train"] and r["quality"] == "valid":
                r["duration_seconds"], r["event"] = 9999999, 0
        write_rows(self.root / "prepared" / "features.jsonl", features)
        write_rows(self.root / "prepared" / "labels.jsonl", labels)
        self.prepared.update(features_sha256=digest(features), labels_sha256=digest(labels))
        self.save_prepared()
        parts["prepared_sha256"] = digest(self.prepared)
        write_json(self.root / "splits.json", parts)
        second = train_baseline(self.root)
        self.assertEqual(first, second)

    def test_delta_bucket_support_and_explicit_global_fallback(self):
        self.prepared["config"]["bin_min_attempts"] = 1
        self.prepared["config"]["bin_min_events"] = 1
        self.save_prepared()
        split(self.root)
        artifact = train_baseline(self.root)
        in_bin = predict(artifact, 1200, 1200, [30, 60, 120])
        self.assertEqual(in_bin["reference"], "delta_bin")
        self.assertEqual(in_bin["d012_probability"], None)
        outside = predict(artifact, 9999, 800, [30])
        self.assertEqual(outside["reference"], "global_fallback")
        self.assertTrue(outside["out_of_domain"])
        self.assertEqual(outside["probabilities"][0]["support"], "extrapolation")

    def test_table_tampering_and_partition_overlap_fail(self):
        parts = split(self.root)
        parts["partitions"]["test"].append(parts["partitions"]["train"][0])
        write_json(self.root / "splits.json", parts)
        with self.assertRaisesRegex(StudyError, "Leaking"):
            train_baseline(self.root)
        parts["partitions"]["test"].pop()
        write_json(self.root / "splits.json", parts)
        path = self.root / "prepared" / "features.jsonl"
        values = list(read_rows(path))
        values[0]["old_rating"] = 9999
        write_rows(path, values)
        with self.assertRaisesRegex(StudyError, "checksum"):
            train_baseline(self.root)

    def test_default_delta_bin_minimum_boundaries(self):
        partitions = split(self.root)
        for count, events, expected_bins in ((49, 20, 0), (50, 19, 0), (50, 20, 1)):
            features, labels = [], []
            for i in range(count):
                cid = partitions["partitions"]["train"][i % 6]
                sid = digest(["threshold-synthetic", i])
                features.append({"sample_id": sid, "contest_id": cid, "old_rating": 1200, "problem_rating": 1200, "delta": 0})
                labels.append({"sample_id": sid, "contest_id": cid, "quality": "valid", "event": int(i < events), "duration_seconds": 60 + i})
            write_rows(self.root / "prepared" / "features.jsonl", features)
            write_rows(self.root / "prepared" / "labels.jsonl", labels)
            self.prepared.update(features_sha256=digest(features), labels_sha256=digest(labels))
            self.save_prepared()
            partitions["prepared_sha256"] = digest(self.prepared)
            write_json(self.root / "splits.json", partitions)
            with self.subTest(count=count, events=events):
                model = train_baseline(self.root)
                self.assertEqual(len(model["delta_bins"]), expected_bins)


if __name__ == "__main__":
    unittest.main()
