from contextlib import redirect_stdout, redirect_stderr
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from lorem_study.cli import main, work_lock
from lorem_study.config import load_config
from lorem_study.fixture import create_fixture
from lorem_study.io import StudyError, read_json, write_json


class CliTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def command(self, command, root=None):
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            status = main([command, "--work", str(root or self.root)])
        return status, out.getvalue(), err.getvalue()

    def test_synthetic_pipeline_is_deterministic_and_unimplemented_stages_explicit(self):
        roots = [self.root / "one", self.root / "two"]
        models = []
        for root in roots:
            for command in ("fixture", "prepare", "split", "train-baseline", "report"):
                status, out, err = self.command(command, root)
                self.assertEqual(status, 0, err)
                self.assertEqual(json.loads(out)["data_kind"], "synthetic")
            models.append(read_json(root / "baseline" / "model.json"))
        self.assertEqual(models[0], models[1])
        self.assertEqual(read_json(roots[0] / "snapshot.json"), read_json(roots[1] / "snapshot.json"))
        for command in ("evaluate",):
            status, _, err = self.command(command)
            self.assertEqual(status, 2)
            self.assertIn("NOT IMPLEMENTED", err)

    def test_invalid_config_rejected_before_collection(self):
        config = load_config()
        for field, value in (("cutoff_utc", "2019-01-01T00:00:00Z"), ("request_interval_seconds", 1),
                              ("budgets_minutes", [120, 30]), ("shared_contest_groups", [[1, 2]]),
                              ("page_size", True), ("protocol_version", "unknown")):
            with self.subTest(field=field):
                path = self.root / "invalid.json"
                write_json(path, {**config, field: value})
                with self.assertRaises(StudyError):
                    load_config(path)

    def test_fixture_never_overwrites_real_or_existing_data(self):
        (self.root / "snapshot.json").write_text('{"data_kind":"real"}')
        with self.assertRaisesRegex(StudyError, "empty directory"):
            create_fixture(self.root, load_config())

    def test_python_module_entry_point(self):
        result = subprocess.run([sys.executable, "-m", "lorem_study", "--help"], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0)
        self.assertIn("train-baseline", result.stdout)

    def test_work_lock_prevents_concurrent_writers_and_releases(self):
        with work_lock(self.root):
            with self.assertRaisesRegex(StudyError, "Another command"):
                with work_lock(self.root):
                    self.fail("Acquired twice")
        with work_lock(self.root):
            pass


if __name__ == "__main__":
    unittest.main()
