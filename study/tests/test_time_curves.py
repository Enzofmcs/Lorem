"""Synthetic software checks; no scientific validation or held-out tuning."""
import copy
import unittest
from pathlib import Path
from lorem_study.baseline import km_fit
from lorem_study.time_curves import export_curves, retained_demo, lookup, limits
from lorem_study.io import read_json

ROOT = Path(__file__).resolve().parents[2]


class TimeCurvesTest(unittest.TestCase):
    def model(self):
        return {"schema_version": 1, "model_version": "kaplan-meier-v1", "data_kind": "synthetic",
                "delta_bin_width": 100, "bin_min_attempts": 10, "bin_min_events": 5,
                "domain": {"old_rating": [800, 2000], "problem_rating": [800, 2400], "delta": [-400, 800]},
                "delta_bins": {"400": km_fit([(600 + i * 60, 1) for i in range(20)])}}

    def test_supported_lookup_is_ordered_and_uses_cf_scale(self):
        artifact = export_curves(self.model())
        self.assertEqual(artifact, read_json(ROOT / "study/tests/fixtures/time-curves-synthetic.json"))
        result = lookup(artifact, 1200, 1600)
        self.assertTrue(result["available"])
        b = result["limits"]
        self.assertTrue(0 < b["t1_ms"] < b["t2_ms"] < b["t3_ms"] <= 7200000)
        self.assertEqual("rating_scale_mismatch", lookup(artifact, 800, 1600, "lorem")["reason"])
        self.assertEqual("missing_prior_rating", lookup(artifact, None, 1600)["reason"])
        self.assertEqual("out_of_domain", lookup(artifact, 2500, 2900)["reason"])
        self.assertEqual("unsupported_region", lookup(artifact, 1200, 1500)["reason"])
        self.assertEqual("unsupported_region", lookup(artifact, 1200, 1700)["reason"])
        self.assertTrue(lookup(artifact, 1200, 1699)["available"])

    def test_sparse_censored_ties_cap_and_missing_quantiles(self):
        model = self.model()
        model["delta_bins"]["400"] = km_fit([(600, 1), (7200, 0)] * 20)
        a = export_curves(model)
        self.assertIsNone(a["regions"][0]["quantiles_minutes"]["0.9"])
        self.assertEqual("quantile_not_identified", lookup(a, 1200, 1600)["reason"])
        model["delta_bins"]["400"] = km_fit([(600, 1)] * 20)
        self.assertEqual("unordered_or_cap_collision", lookup(export_curves(model), 1200, 1600)["reason"])
        model = self.model()
        model["bin_min_attempts"] = 100
        self.assertEqual("insufficient_sample_support", lookup(export_curves(model), 1200, 1600)["reason"])
        self.assertEqual(7200000, limits({"0.5": 60, "0.75": 90, "0.9": 150})[0]["t3_ms"])
        self.assertEqual("unordered_or_cap_collision", limits({"0.5": 90, "0.75": 120, "0.9": 150})[1])

    def test_retained_demo_export_is_exact_and_reproducible(self):
        artifact = retained_demo(read_json(ROOT / "study/results/2026-10-18/report/demo.json"))
        self.assertEqual(artifact, read_json(ROOT / "app/src/main/assets/time-curves-v1.json"))
        self.assertEqual("quantile_not_identified", lookup(artifact, 1200, 1600)["reason"])
        self.assertEqual("unsupported_region", lookup(artifact, 1201, 1601)["reason"])
        self.assertEqual("disabled", artifact["activation"])

    def test_export_does_not_mutate_model_or_depend_on_test_labels(self):
        model = self.model()
        original = copy.deepcopy(model)
        a = export_curves(model)
        self.assertEqual(original, model)
        self.assertEqual(a, export_curves(model))
        damaged = copy.deepcopy(a)
        damaged["regions"][0]["limits"]["t2_ms"] = 1
        self.assertEqual("invalid_limits", lookup(damaged, 1200, 1600)["reason"])
