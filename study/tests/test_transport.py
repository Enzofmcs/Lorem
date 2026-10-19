from copy import deepcopy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

try:
    import numpy as np
    from lorem_study.evaluate import bootstrap_draws
    from lorem_study.transport_stats import (assess_gate, calibration, fit_mapping, make_predictions,
                                            partition_indices, rating_pairs, select_candidate)
    AVAILABLE = True
except ImportError:
    AVAILABLE = False

from lorem_study.baseline import km_fit
from lorem_study.io import StudyError, digest, read_json, read_rows, write_json, write_rows
from lorem_study.ipsum import prepare_ipsums
from lorem_study.transport import TRANSPORT, evaluate_ipsums, freeze_transport
from test_ipsum import fetch, fixture


@unittest.skipUnless(AVAILABLE, "Optional model dependencies not installed")
class TransportTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.source = self.root / "source"
        curve = km_fit([(20*60, 1), (40*60, 1), (80*60, 1), (140*60, 0)])
        domain = {"old_rating": [100, 4000], "problem_rating": [100, 4000], "delta": [-4000, 4000]}
        self.baseline = {"schema_version": 1, "model_version": "kaplan-meier-v1", "data_kind": "synthetic",
                         "training_contest_ids": [101], "global": curve, "delta_bins": {}, "delta_bin_width": 200,
                         "domain": domain}
        self.aft = {"schema_version": 1, "model_version": "aft-v1", "data_kind": "synthetic",
                    "training_contest_ids": [101], "selection": {"status": "selected"},
                    "fit": {"family": "weibull", "coefficients": [3.7, 0.1, 0.1],
                            "center": [13, 2], "scale": 0.8, "max_followup_minutes": 140, "domain": domain}}
        self.write_source()

    def write_source(self):
        write_json(self.source / "baseline/model.json", self.baseline)
        write_json(self.source / "aft/model.json", self.aft)
        write_json(self.source / "evaluation/plan.json",
                   {"schema_version": 1, "baseline_sha256": digest(self.baseline), "aft_sha256": digest(self.aft)})

    def prepare(self, people=100, n=20, max_minutes=140):
        paths = []
        for person in range(1, people+1):
            value = fixture(person, n)
            for j, row in enumerate(value["attempts"]):
                row["lorem_rating_before"] = 1000 + (person % 10)*80 + j
                row["cf_rating_before"] = 200 + row["lorem_rating_before"]
                duration = min(max_minutes, 20 + (j % 7)*20)*60000
                row["ended_at_epoch_ms"] = row["started_at_epoch_ms"] + duration
                if row["status"] == "COMPLETED":
                    row["first_ac_at_epoch_ms"] = row["ended_at_epoch_ms"]
            path = self.root / f"export-{person}.json"
            write_json(path, value); paths.append(path)
        return prepare_ipsums(self.root, paths, metadata_fetch=fetch)

    def test_no_data_freezes_protocol_without_importing_numeric_code_or_metrics(self):
        prepare_ipsums(self.root, offline=True)
        with patch("lorem_study.transport_stats.evaluate_cohort", side_effect=AssertionError("must not evaluate")):
            result = evaluate_ipsums(self.root, self.source)
        self.assertEqual(result["status"], "insufficient_data")
        self.assertEqual(result["data_kind"], "not_collected")
        output = self.root / "ipsum/transport"
        self.assertTrue((output / "plan.json").exists())
        self.assertFalse((output / "metrics.json").exists())
        self.assertFalse((output / "selection.json").exists())
        self.assertEqual(result["network_requests"], 0)

    def test_preparation_floor_does_not_meet_final_evidence_floor(self):
        self.prepare(10)
        result = evaluate_ipsums(self.root, self.source)
        self.assertEqual(result["status"], "insufficient_data")
        self.assertFalse(result["test_evaluated"])
        self.assertEqual(result["partitions"]["test"]["participants"], 2)

    def test_mapping_weights_participants_equally_and_uses_only_prior_ratings(self):
        rows = [{"participant_key": "a", "lorem_rating_before": 1000, "cf_rating_before": 1200}]*20
        rows += [{"participant_key": "b", "lorem_rating_before": 1500, "cf_rating_before": 1700},
                 {"participant_key": "c", "lorem_rating_before": 2000, "cf_rating_before": 2200}]
        mapping = fit_mapping(rows)
        self.assertAlmostEqual(mapping["slope"], 1)
        self.assertAlmostEqual(mapping["intercept"], 200)
        polluted = [{**r, "lorem_rating_after": 9999, "event": 0, "duration_seconds": 1} for r in rows]
        self.assertEqual(mapping, fit_mapping(polluted))
        self.assertIn("unavailable", fit_mapping(rows[:20])["status"])
        unequal = [*rows, {"participant_key": "c", "lorem_rating_before": 2000, "cf_rating_before": 3000}]
        unreplicated = unequal[:1] + unequal[20:]
        self.assertEqual(fit_mapping(unequal)["slope"], fit_mapping(unreplicated)["slope"])

    def test_correlation_one_does_not_hide_rating_offset(self):
        rows = [{"participant_key": str(i), "cf_rating_before": 1000+i*100,
                 "lorem_rating_before": 1400+i*100, "event": 1} for i in range(20)]
        people, indices = partition_indices(rows)
        result = rating_pairs(rows, indices, bootstrap_draws(people, 9))
        self.assertAlmostEqual(result["metrics"]["pearson"]["estimate"], 1)
        self.assertEqual(result["metrics"]["lorem_minus_cf_bias"]["estimate"], 400)

    def test_continuous_mapped_ratings_use_the_delta_bin_instead_of_global_fallback(self):
        baseline = deepcopy(self.baseline)
        baseline["delta_bins"] = {"0": km_fit([(10*60, 1), (140*60, 0)]),
                                  "-200": km_fit([(5*60, 1), (140*60, 0)])}
        row = {"lorem_rating_before": 1200, "cf_rating_before": 1200, "problem_rating": 1400}
        mapping = {"status": "available", "intercept": 100.5, "slope": 1}
        result = make_predictions([row], mapping, baseline, self.aft)
        # Delta 99.5 belongs to bin 0, not global (whose median is 40 min).
        self.assertEqual(result["km_delta/linear_train_only"]["q"][0, 1], 10)
        mapping["intercept"] = 300.5  # Delta -100.5 belongs to bin -200.
        result = make_predictions([row], mapping, baseline, self.aft)
        self.assertEqual(result["km_delta/linear_train_only"]["q"][0, 1], 5)

    def test_ipcw_calibration_recomputes_absolute_bin_gaps_per_bootstrap(self):
        # Two participants, one bin. Point gap cancels; resamples need not cancel.
        terms = {}
        for band in range(5):
            key = f"probability/30/bin/{band}"
            terms[key + "/observed_ipcw"] = np.array([0., 1.]) if band == 2 else np.full(2, np.nan)
            terms[key + "/prediction_all"] = np.array([.5, .5]) if band == 2 else np.full(2, np.nan)
        result = calibration(terms, np.array([0, 1]), np.array([[2, 0], [1, 1], [0, 2]]), 30)
        self.assertEqual(result["estimate"], 0)
        self.assertEqual(result["ci95"][1], .5)

    def good_score(self):
        def metric(estimate, lo, hi): return {"estimate": estimate, "ci95": [lo, hi], "n": 400}
        metrics = {"time/mae": metric(15, 12, 18),
                   "distribution/integrated_brier_ipcw": metric(.12, .10, .14)}
        metrics.update({f"probability/{b}/ece_ipcw": metric(.03, .01, .06) for b in (30, 60, 120)})
        return {"metrics": metrics, "support": {"integration_range_minutes": [0, 120]},
                "in_domain_fraction": 1, "median_coverage_events": 1}

    def test_gate_checks_ci_precision_domain_quantile_coverage_and_all_budgets(self):
        good = self.good_score()
        self.assertTrue(assess_gate(good, TRANSPORT)["passed"])
        for label, mutate in (
            ("precision", lambda r: r["metrics"]["time/mae"].update(ci95=[1, 19])),
            ("upper", lambda r: r["metrics"]["distribution/integrated_brier_ipcw"].update(ci95=[.14, .16])),
            ("calibration", lambda r: r["metrics"]["probability/120/ece_ipcw"].update(ci95=None)),
            ("domain", lambda r: r.update(in_domain_fraction=.89)),
            ("quantile", lambda r: r.update(median_coverage_events=.94)),
            ("support", lambda r: r["support"].update(integration_range_minutes=[0, 60])),
        ):
            value = deepcopy(good); mutate(value)
            self.assertFalse(assess_gate(value, TRANSPORT)["passed"], label)
        scores = {"aft/identity": good, "km_delta/identity": deepcopy(good), "aft/cf_reference": good}
        self.assertEqual(select_candidate(scores, TRANSPORT), "km_delta/identity")
        scores["km_delta/identity"]["support"]["integration_range_minutes"] = [0, 60]
        self.assertEqual(select_candidate(scores, TRANSPORT), "aft/identity")

    def test_hash_tamper_and_leaking_partition_are_rejected(self):
        self.prepare(10)
        path = self.root / "ipsum/attempts.jsonl"
        rows = list(read_rows(path)); rows[0]["lorem_rating_before"] += 1; write_rows(path, rows)
        with self.assertRaisesRegex(StudyError, "checksum"): evaluate_ipsums(self.root, self.source)
        # Fresh evaluation binding for a deliberately invalid split.
        (self.root / "ipsum/transport/cohort.json").unlink()
        manifest = read_json(self.root / "ipsum/manifest.json"); manifest["attempts_sha256"] = digest(rows)
        write_json(self.root / "ipsum/manifest.json", manifest)
        partitions = read_json(self.root / "ipsum/partitions.json")
        partitions["participants"]["test"] += partitions["participants"]["train"][:1]
        write_json(self.root / "ipsum/partitions.json", partitions)
        with self.assertRaisesRegex(StudyError, "Leaking"): evaluate_ipsums(self.root, self.source)

    def test_model_and_protocol_changes_cannot_reopen_frozen_evaluation(self):
        prepare_ipsums(self.root, offline=True)
        evaluate_ipsums(self.root, self.source)
        self.aft["fit"]["scale"] = .9; self.write_source()
        with self.assertRaisesRegex(StudyError, "Frozen"): evaluate_ipsums(self.root, self.source)
        with patch.dict(TRANSPORT, {"mae_upper_ci_max_minutes": 99}):
            with self.assertRaisesRegex(StudyError, "Frozen"): freeze_transport(self.root, self.source)

    def test_full_synthetic_pipeline_is_deterministic_and_never_authorizes_integration(self):
        self.prepare()
        result = evaluate_ipsums(self.root, self.source)
        output = self.root / "ipsum/transport"
        before = {p.name: p.read_bytes() for p in output.iterdir()}
        self.assertEqual(result, evaluate_ipsums(self.root, self.source))
        self.assertEqual(before, {p.name: p.read_bytes() for p in output.iterdir()})
        self.assertEqual(result["status"], "synthetic_software_check")
        self.assertEqual(result["integration_gate"], "CLOSED")
        self.assertNotIn("eligible_for_domain_decision", result["decision"])
        metrics = read_json(output / "metrics.json")
        self.assertTrue(metrics["test_evaluated"])
        self.assertAlmostEqual(metrics["selection"]["mapping"]["intercept"], 200)
        self.assertIn("paired_differences", metrics)
        self.assertEqual(metrics["strata_selected"]["interruptions_recorded"]["counts"]["attempts"], 0)
        self.assertEqual(metrics["rating_pairs"]["test"]["counts"]["participants"], 20)

    def test_even_passing_synthetic_metrics_never_become_real_transport_evidence(self):
        self.prepare()
        with patch("lorem_study.transport_stats.assess_gate", return_value={"passed": True, "failures": []}):
            result = evaluate_ipsums(self.root, self.source)
        self.assertTrue(result["numeric_gate_passed"])
        self.assertEqual(result["integration_gate"], "CLOSED")
        self.assertEqual(result["decision"], "do_not_integrate; retain_distinct_ratings")

    def test_no_supported_validation_candidate_keeps_test_closed(self):
        self.prepare(max_minutes=1)
        result = evaluate_ipsums(self.root, self.source)
        self.assertFalse(result["test_evaluated"])
        self.assertFalse(result["numeric_gate_passed"])
        metrics = read_json(self.root / "ipsum/transport/metrics.json")
        self.assertIsNone(metrics["selection"]["chosen"])
        self.assertNotIn("test", metrics)

    def test_test_predictions_are_computed_only_after_selection_is_persisted(self):
        self.prepare()
        parts = read_json(self.root / "ipsum/partitions.json")["participants"]
        test_people = set(parts["test"])
        def check_order(rows, *args):
            if any(r["participant_key"] in test_people for r in rows):
                self.assertTrue((self.root / "ipsum/transport/selection.json").exists())
            return make_predictions(rows, *args)
        with patch("lorem_study.transport_stats.make_predictions", side_effect=check_order):
            evaluate_ipsums(self.root, self.source)

    def test_test_outcomes_do_not_affect_mapping_or_selection(self):
        self.prepare()
        evaluate_ipsums(self.root, self.source)
        output = self.root / "ipsum/transport"
        selected = read_json(output / "selection.json")
        table = list(read_rows(self.root / "ipsum/attempts.jsonl"))
        people = read_json(self.root / "ipsum/partitions.json")["participants"]["test"]
        for row in table:
            if row["participant_key"] in people: row["duration_seconds"] *= 2
        # Invoke the numerical layer in isolation: orchestrator rejects changing a bound cohort.
        from lorem_study.transport_stats import evaluate_cohort
        parts = read_json(self.root / "ipsum/partitions.json")["participants"]
        by_part = {name: [r for r in table if r["participant_key"] in ids] for name, ids in parts.items()}
        plan = read_json(output / "plan.json")
        metrics = evaluate_cohort(by_part, self.baseline, self.aft, plan, output)
        self.assertEqual(metrics["selection"], selected)

    def test_real_observations_cannot_use_synthetic_contest_models(self):
        self.prepare()
        path = self.root / "ipsum/manifest.json"
        manifest = read_json(path); manifest["data_kind"] = "ipsum_local"; write_json(path, manifest)
        with self.assertRaisesRegex(StudyError, "real frozen"): evaluate_ipsums(self.root, self.source)

    def test_real_model_label_cannot_substitute_unregistered_models(self):
        self.baseline["data_kind"] = self.aft["data_kind"] = "real"
        self.write_source()
        prepare_ipsums(self.root, offline=True)
        with self.assertRaisesRegex(StudyError, "exact preregistered"): evaluate_ipsums(self.root, self.source)


if __name__ == "__main__":
    unittest.main()
