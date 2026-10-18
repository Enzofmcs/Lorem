from pathlib import Path
import tempfile
import unittest

try:
    import numpy as np
    from lorem_study.evaluate import HORIZONS, bootstrap_draws, evaluate, metric_terms, summarize
    from lorem_study.aft import train_aft
    from test_aft import write_dataset
    AVAILABLE = True
except ImportError:
    AVAILABLE = False

from lorem_study.baseline import km_fit, train_baseline
from lorem_study.io import StudyError, digest, read_json, write_json


@unittest.skipUnless(AVAILABLE, "Optional model dependencies not installed")
class EvaluationTests(unittest.TestCase):
    def prediction(self, n, p=.25, q=(2, 10, 20)):
        return {"p": np.full((n, len(HORIZONS)), p), "q": np.tile(q, (n, 1)).astype(float)}

    def test_hand_calculation_times_intervals_known_censoring_and_ipcw(self):
        censoring = km_fit([(300, 1), (900, 1), (12000, 1)], reverse=True)
        terms, support = metric_terms([10, 20, 30, 40], [1, 0, 0, 1], self.prediction(4), censoring)
        metrics, _ = summarize(terms, np.array([0, 0, 1, 1]), np.array([[1, 1], [2, 0], [0, 2]]))
        self.assertAlmostEqual(metrics["time/mae"]["estimate"], 15)
        self.assertAlmostEqual(metrics["time/median_absolute_error"]["estimate"], 15)
        self.assertAlmostEqual(metrics["time/rmse"]["estimate"], np.sqrt(450))
        self.assertEqual(metrics["time/bias"]["estimate"], -15)
        self.assertEqual(metrics["interval80/coverage"]["estimate"], .5)
        self.assertEqual(metrics["interval80/width_minutes"]["estimate"], 18)
        self.assertEqual(metrics["probability/30/brier_known"]["n"], 3)
        self.assertAlmostEqual(metrics["probability/30/brier_known"]["estimate"], (.75**2 + 2*.25**2) / 3)
        self.assertAlmostEqual(metrics["probability/30/brier_ipcw"]["estimate"], (.75**2 + 2*.25**2) / 4)
        self.assertEqual(support["horizons"][0]["unknown"], 1)
        self.assertAlmostEqual(metrics["probability/30/log_loss_known"]["estimate"], (-np.log(.25)-2*np.log(.75))/3)

    def test_nontrivial_censor_weights_and_unsupported_horizons(self):
        censoring = km_fit([(600, 0), (1200, 1)], reverse=True)
        terms, support = metric_terms([5, 10, 15], [1, 0, 0], self.prediction(3), censoring)
        self.assertTrue(np.all(np.isnan(terms["probability/30/brier_ipcw"])))
        self.assertEqual(support["integration_range_minutes"], [0, 20])
        censoring = km_fit([(600, 0), (7200, 1)], reverse=True)
        terms, _ = metric_terms([5, 30, 40], [1, 0, 0], self.prediction(3), censoring)
        self.assertAlmostEqual(terms["probability/30/brier_ipcw"].mean(), (.75**2 + 2*.25**2/.5)/3)
        zero_g = km_fit([(600, 0)], reverse=True)
        terms, _ = metric_terms([5, 40], [1, 0], self.prediction(2), zero_g)
        self.assertTrue(np.all(np.isnan(terms["probability/30/brier_ipcw"])))

    def test_cluster_resampling_counts_medians_and_reproducibility(self):
        draws = bootstrap_draws([[1, 2], [3]], 77)
        np.testing.assert_array_equal(draws, bootstrap_draws([[1, 2], [3]], 77))
        self.assertTrue(np.all(draws.sum(axis=1) == 2))
        metrics, samples = summarize({"time/mae": [1, 2, 100], "time/median_absolute_error": [1, 2, 100]},
                                      np.array([0, 0, 1]), np.array([[2, 0], [0, 2], [1, 1]]))
        np.testing.assert_allclose(samples["time/mae"], [1.5, 100, 103/3])
        np.testing.assert_allclose(samples["time/median_absolute_error"], [1.5, 100, 2])
        self.assertEqual(metrics["time/mae"]["n"], 3)

    def test_missing_predictions_zero_one_and_unknown_are_explicit(self):
        pred = self.prediction(3)
        pred["p"][0] = 0
        pred["p"][1] = 1
        pred["p"][2] = np.nan
        pred["q"][:] = np.nan
        terms, _ = metric_terms([5, 40, 20], [1, 1, 0], pred,
                                km_fit([(7200, 1)], reverse=True))
        self.assertTrue(np.isfinite(terms["probability/30/log_loss_known"][:2]).all())
        self.assertTrue(np.isnan(terms["probability/30/log_loss_known"][2]))
        self.assertTrue(np.isnan(terms["time/mae"]).all())
        self.assertTrue(np.isnan(terms["interval80/coverage"]).all())

    def test_frozen_pipeline_replay_pairing_and_model_tampering(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_dataset(root)
            train_baseline(root)
            train_aft(root)
            first = evaluate(root)
            self.assertEqual(first["test_contest_ids"], [9, 10])
            self.assertEqual(first["n"], 120)
            self.assertEqual(first["bootstrap"]["replicates"], 200)
            self.assertEqual(digest(first), digest(evaluate(root)))
            self.assertIsNone(first["models"]["d012"]["metrics"]["probability/30/brier_known"]["estimate"])
            paired = first["paired_differences_common_support"]["aft_minus_km_delta"]["time/mae"]
            self.assertGreater(paired["n_common"], 0)
            self.assertIsNotNone(paired["ci95"])
            aft = read_json(root / "aft" / "model.json")
            aft["fit"]["coefficients"][0] += .1
            write_json(root / "aft" / "model.json", aft)
            with self.assertRaisesRegex(StudyError, "already frozen"):
                evaluate(root)
