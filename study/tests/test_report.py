import hashlib
from pathlib import Path
import tempfile
import unittest

try:
    from importlib.util import find_spec
    from lorem_study.aft import train_aft
    from lorem_study.evaluate import evaluate
    from test_aft import write_dataset
    AVAILABLE = find_spec("matplotlib") is not None
except ImportError:
    AVAILABLE = False

from lorem_study.baseline import train_baseline
from lorem_study.io import StudyError, digest, read_json, write_json
from lorem_study.report import report


class ReportTests(unittest.TestCase):
    def test_missing_evaluation_is_status_only(self):
        with tempfile.TemporaryDirectory() as directory:
            result = report(Path(directory))
            self.assertIn("pipeline_status_only", result["status"])
            self.assertFalse((Path(directory) / "report" / "REPORT.md").exists())

    @unittest.skipUnless(AVAILABLE, "Optional report dependencies not installed")
    def test_all_tables_figures_demo_replay_and_integrity(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_dataset(root)
            train_baseline(root)
            train_aft(root)
            evaluate(root)
            first = report(root)
            self.assertEqual(first["data_kind"], "synthetic")
            self.assertTrue({"REPORT.md", "metrics.csv", "paired-differences.csv", "rating-slices.csv",
                             "calibration.csv", "time-mae.svg", "calibration-ipcw.png", "demo.json"} <= set(first["files"]))
            for name, sha in first["files"].items():
                self.assertEqual(hashlib.sha256((root / "report" / name).read_bytes()).hexdigest(), sha)
            text = (root / "report" / "REPORT.md").read_text()
            self.assertIn("**synthetic**", text)
            self.assertIn("vies de selecao", text)
            self.assertEqual(digest(first), digest(report(root)))
            demo = read_json(root / "report" / "demo.json")
            self.assertEqual(demo["budgets_minutes"], [30, 60, 120])
            metrics = read_json(root / "evaluation" / "metrics.json")
            metrics["n"] += 1
            write_json(root / "evaluation" / "metrics.json", metrics)
            with self.assertRaisesRegex(StudyError, "checksum"):
                report(root)
