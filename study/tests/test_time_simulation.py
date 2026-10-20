"""Mechanical policy invariants on declared synthetic schedules, never empirical gates."""
from pathlib import Path
import unittest
from lorem_study.time_simulation import simulate, score
from lorem_study.io import read_json


class TimeSimulationTest(unittest.TestCase):
    def test_report_reproducible_and_never_activates(self):
        result = simulate()
        path = Path(__file__).resolve().parents[1] / "results/time-scoring-v1/scenarios.json"
        self.assertEqual(result, read_json(path))
        self.assertEqual("synthetic", result["data_kind"])
        self.assertEqual("disabled", result["activation"])
        self.assertEqual(120, len(result["trajectories"]))
        self.assertEqual(9, len(result["sensitivity"]))
        for r in result["trajectories"]:
            self.assertEqual(11, len(r["ratings"]))
            if r["schedule"] == "all_zero":
                self.assertEqual(r["initial_rating"], r["ratings"][-1])
                self.assertGreater(r["legacy_final_delta"], 0)

    def test_waiting_never_improves_gain_and_hint_never_makes_ac_negative(self):
        for base in range(41):
            for hint in (False, True):
                for bonus in (1, 1.1, 1.25):
                    for reduction in (.25, .5, .75):
                        values = [score(base, t, [1000, 2000, 3000], hint, bonus, reduction)["gain"]
                                  for t in (0, 999, 1000, 1001, 1999, 2000, 2999, 3000, 3001)]
                        self.assertGreaterEqual(min(values), 0)
                        self.assertTrue(all(a >= b for a, b in zip(values, values[1:])))
