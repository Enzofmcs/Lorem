from pathlib import Path
from contextlib import redirect_stdout, redirect_stderr
import io
import json
from unittest.mock import patch
import tempfile
import unittest

try:
    import numpy as np
    from lorem_study.aft import cdf, fit, grouped_folds, objective, predict_aft, train_aft
    from lorem_study.scoring import validation_score
    AVAILABLE = True
except ImportError:
    AVAILABLE = False

from lorem_study.baseline import km_fit
from lorem_study.config import load_config
from lorem_study.cli import main
from lorem_study.io import StudyError, digest, read_json, read_rows, write_json, write_rows


def synthetic_rows(n=1800, family="lognormal", seed=20261018, contest_id=1):
    rng = np.random.default_rng(seed)
    old = rng.integers(800, 2400, n)
    delta = rng.integers(-400, 800, n)
    mu = np.log(32) - .03 * (old / 100 - 16) + .08 * (delta / 100 - 2)
    if family == "lognormal":
        actual = np.exp(mu + .4 * rng.standard_normal(n))
    else:
        actual = np.exp(mu) * rng.exponential(size=n) ** .65
    censor = rng.uniform(25, 110, n)
    rows = []
    for i in range(n):
        key = f"synthetic-{contest_id}-{i}"
        rows.append(({"sample_id": key, "contest_id": contest_id, "old_rating": int(old[i]),
                      "problem_rating": int(old[i] + delta[i]), "delta": int(delta[i])},
                     {"sample_id": key, "contest_id": contest_id, "quality": "valid", "reason": None,
                      "duration_seconds": float(min(actual[i], censor[i]) * 60), "event": int(actual[i] <= censor[i])}))
    return rows


def write_dataset(root):
    rows = [row for cid in range(1, 11) for row in synthetic_rows(60, seed=42 + cid, contest_id=cid)]
    features, labels = [f for f, _ in rows], [r for _, r in rows]
    write_rows(root / "prepared" / "features.jsonl", features)
    write_rows(root / "prepared" / "labels.jsonl", labels)
    prepared = {"schema_version": 1, "data_kind": "synthetic", "config": load_config(),
                "features_sha256": digest(features), "labels_sha256": digest(labels),
                "complete_contest_ids": list(range(1, 11))}
    write_json(root / "prepared" / "manifest.json", prepared)
    write_json(root / "splits.json", {"schema_version": 1, "data_kind": "synthetic", "seed": 20261018,
               "prepared_sha256": digest(prepared), "groups": [[i] for i in range(1, 11)],
               "partitions": {"train": list(range(1, 7)), "validation": [7, 8], "test": [9, 10]}})


@unittest.skipUnless(AVAILABLE, "Optional NumPy/SciPy AFT dependencies not installed")
class AftTests(unittest.TestCase):
    def test_analytic_likelihood_gradients_for_both_distributions(self):
        rows = synthetic_rows(50)
        raw = np.array([[f["old_rating"] / 100, f["delta"] / 100] for f, _ in rows])
        x = np.column_stack([np.ones(50), raw - raw.mean(axis=0)])
        log_t = np.log([r["duration_seconds"] / 60 for _, r in rows])
        event = np.array([r["event"] for _, r in rows])
        theta = np.array([3.2, -.04, .07, -.3])
        for family in ("lognormal", "weibull"):
            loss, gradient = objective(theta, x, log_t, event, family, .01)
            finite_difference = []
            for i in range(4):
                plus, minus = theta.copy(), theta.copy()
                plus[i] += 1e-6
                minus[i] -= 1e-6
                finite_difference.append((objective(plus, x, log_t, event, family, .01)[0] -
                                          objective(minus, x, log_t, event, family, .01)[0]) / 2e-6)
            self.assertTrue(np.isfinite(loss))
            np.testing.assert_allclose(gradient, finite_difference, atol=1e-5)

    def test_censored_signal_recovery_lognormal_and_weibull(self):
        for family, scale in (("lognormal", .4), ("weibull", .65)):
            rows = synthetic_rows(family=family)
            model = fit(rows, family, 0)
            self.assertGreater(model["training_censored"], 0)
            self.assertAlmostEqual(model["coefficients"][1], -.03, delta=.02)
            self.assertAlmostEqual(model["coefficients"][2], .08, delta=.02)
            self.assertAlmostEqual(model["scale"], scale, delta=.08)
            values = cdf(model, [rows[0][0]], [0, 1, 10, 30, 60, 120])[0]
            self.assertEqual(values[0], 0)
            self.assertTrue(np.all(np.diff(values) >= 0))
            self.assertTrue(np.all((values >= 0) & (values <= 1)))

    def test_failures_are_explicit_not_fabricated_fits(self):
        rows = synthetic_rows(100)
        censored = [(f, {**r, "event": 0}) for f, r in rows]
        with self.assertRaisesRegex(StudyError, "five observed events"):
            fit(censored, "lognormal", .01)
        collinear = [({**f, "old_rating": 1200}, r) for f, r in rows]
        with self.assertRaisesRegex(StudyError, "rank deficient"):
            fit(collinear, "lognormal", .01)
        with self.assertRaisesRegex(StudyError, "convergence"):
            fit(rows, "lognormal", .01, maxiter=1)
        from types import SimpleNamespace
        stalled = SimpleNamespace(success=True, x=np.array([3.0, 0, 0, 0]), fun=4.0,
                                  nit=1, message="CONVERGENCE: RELATIVE REDUCTION", jac=np.zeros(4))
        with patch("lorem_study.aft.minimize", return_value=stalled), self.assertRaisesRegex(StudyError, "convergence"):
            fit(rows, "weibull", .01)

    def test_internal_brier_accounts_for_known_and_unknown_censoring(self):
        censoring = km_fit([(600, 1), (1200, 1), (1800, 1)], reverse=True)
        p = np.tile([0, .25, .75], (3, 1))
        scores = validation_score([5, 15, 25], [1, 1, 0], p, [0, 10, 20], censoring)
        self.assertAlmostEqual(scores["horizons"][1]["brier_ipcw"], (.75 ** 2 + 2 * .25 ** 2) / 3)
        unknown = validation_score([5, 15, 25], [0, 1, 0], p, [0, 10, 20], censoring)
        self.assertEqual(unknown["horizons"][1]["unknown"], 1)
        self.assertAlmostEqual(unknown["horizons"][1]["brier_ipcw"], 2 * .25 ** 2 / 3)
        unsupported = validation_score([5, 15, 25], [1, 1, 0], np.tile([0, .5], (3, 1)), [0, 31], censoring)
        self.assertIsNone(unsupported["horizons"][1]["brier_ipcw"])

    def test_grouped_tuning_is_deterministic_and_test_labels_never_change_model(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_dataset(root)
            first = train_aft(root)
            self.assertEqual(len(first["selection"]["candidates"]), 6)
            output, error = io.StringIO(), io.StringIO()
            with redirect_stdout(output), redirect_stderr(error):
                status = main(["train", "--work", str(root)])
            self.assertEqual(status, 0, error.getvalue())
            self.assertEqual(digest(first), digest(json.loads(output.getvalue())))
            output = io.StringIO()
            with redirect_stdout(output):
                status = main(["predict", "--model", "aft", "--work", str(root),
                               "--old-rating", "1600", "--problem-rating", "1800"])
            self.assertEqual(status, 0)
            self.assertEqual(json.loads(output.getvalue())["model_version"], "aft-v1")
            splits = read_json(root / "splits.json")
            folds = grouped_folds(splits)
            self.assertEqual(sorted(cid for fold in folds for cid in fold), list(range(1, 7)))
            self.assertTrue(all(not set(fold) & {7, 8, 9, 10} for fold in folds))
            labels = list(read_rows(root / "prepared" / "labels.jsonl"))
            for row in labels:
                if row["contest_id"] in (9, 10):
                    row.update(duration_seconds=1e8, event=1 - row["event"])
            write_rows(root / "prepared" / "labels.jsonl", labels)
            prepared = read_json(root / "prepared" / "manifest.json")
            prepared["labels_sha256"] = digest(labels)
            write_json(root / "prepared" / "manifest.json", prepared)
            splits["prepared_sha256"] = digest(prepared)
            write_json(root / "splits.json", splits)
            self.assertEqual(digest(first), digest(train_aft(root)))
            predictions = list(read_rows(root / "aft" / "predictions.jsonl"))
            self.assertFalse({"event", "duration_seconds", "rank", "newRating"} & predictions[0].keys())
            splits["groups"] = [[1, 7]] + [[i] for i in range(2, 11) if i != 7]
            write_json(root / "splits.json", splits)
            with self.assertRaisesRegex(StudyError, "leaks"):
                train_aft(root)

    def test_prediction_quantiles_support_extrapolation_and_invalid_query(self):
        model = fit(synthetic_rows(), "lognormal", .01)
        artifact = {"schema_version": 1, "model_version": "aft-v1", "data_kind": "synthetic",
                    "fit": model, "selection": {"status": "selected"}}
        result = predict_aft(artifact, 1600, 1800, [1, 30, 60, 10000])
        self.assertEqual(result["probabilities"][-1]["support"], "insufficient_followup")
        self.assertIsNone(result["probabilities"][-1]["p_ac"])
        quantiles = [result["predictive_interval_80_minutes"][0], result["median_minutes"], result["predictive_interval_80_minutes"][1]]
        present = [q for q in quantiles if q is not None]
        self.assertEqual(present, sorted(present))
        outside = predict_aft(artifact, 4000, 4800, [30])
        self.assertTrue(outside["out_of_domain"])
        self.assertEqual(outside["probabilities"][0]["support"], "extrapolation")
        with self.assertRaises(StudyError):
            predict_aft(artifact, 1600, 1800, [float("nan")])
        with self.assertRaisesRegex(StudyError, "Invalid AFT parameters"):
            predict_aft({**artifact, "fit": {**model, "family": "unknown"}}, 1600, 1800, [30])
        artifact["selection"]["status"] = "calibration_gate_failed; diagnostic_only"
        self.assertIsNone(predict_aft(artifact, 1600, 1800, [30])["probabilities"][0]["p_ac"])
