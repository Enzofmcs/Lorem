from contextlib import redirect_stderr, redirect_stdout
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from lorem_study.cli import main
from lorem_study.io import digest, write_json
from lorem_study.ipsum import prepare_ipsums
from lorem_study.transport import evaluate_ipsums


class TransportCoreTests(unittest.TestCase):
    def test_empty_cohort_and_cli_need_no_numeric_dependencies(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "source"
            baseline = {"schema_version": 1, "model_version": "kaplan-meier-v1",
                        "data_kind": "synthetic", "training_contest_ids": [1]}
            aft = {"schema_version": 1, "model_version": "aft-v1", "data_kind": "synthetic",
                   "training_contest_ids": [1], "selection": {"status": "selected"}}
            write_json(source / "baseline/model.json", baseline)
            write_json(source / "aft/model.json", aft)
            write_json(source / "evaluation/plan.json",
                       {"baseline_sha256": digest(baseline), "aft_sha256": digest(aft)})
            prepare_ipsums(root, offline=True)
            with patch.dict("sys.modules", {"numpy": None, "scipy": None, "lorem_study.transport_stats": None}):
                value = evaluate_ipsums(root, source)
                with redirect_stdout(io.StringIO()):
                    code = main(["evaluate-ipsums", "--work", str(root), "--source-work", str(source)])
            self.assertEqual(code, 0)
            self.assertEqual(value["status"], "insufficient_data")
            self.assertFalse(value["test_evaluated"])
            self.assertFalse((root / "ipsum/transport/metrics.json").exists())

    def test_cli_requires_explicit_source(self):
        with tempfile.TemporaryDirectory() as tmp, redirect_stderr(io.StringIO()):
            self.assertEqual(main(["evaluate-ipsums", "--work", tmp]), 2)
