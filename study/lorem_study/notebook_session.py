"""Explicit offline notebook inputs, historical aggregates, and public exports."""
import csv
import os
from pathlib import Path

from .io import StudyError, digest, read_json, write_json
from .notebook_analysis import (file_sha, freeze_plan, load_attempts, validate_plan,
                                verify_snapshot)


INVENTORY = [
    ("Coleta", "api.py / collect.py / selection.py", "00: snapshot, hashes; CLI collect opt-in"),
    ("Preparo", "prepare.py / io.py", "01: join, funil, âncora e censura"),
    ("KM", "baseline.py / notebook_analysis.py", "02–03: grade x/y, delta, quartis"),
    ("AFT", "aft.py", "04: comparação histórica, sem retreino"),
    ("Avaliação/relatório", "evaluate.py / report.py", "04: resultados de 18/10 e denominadores"),
    ("Curvas/pontos", "time_curves.py / scoring.py / time_simulation.py", "TIME_SCORING.md; v1 desabilitada, apêndice histórico"),
    ("Ipsum/transporte", "ipsum.py / transport.py / transport_stats.py", "IPSUM_PROTOCOL.md / TRANSPORT_PROTOCOL.md; 31 retirada, sem coorte obrigatória"),
]


def study_directory(start=None):
    start = Path(start or os.getcwd()).resolve()
    for path in (start, *start.parents):
        if (path / "lorem_study/notebook_session.py").is_file():
            return path
        if (path / "study/lorem_study/notebook_session.py").is_file():
            return path / "study"
    raise StudyError("Set working directory to repository/study or install and pass an explicit study path")


class Session:
    def __init__(self, study, *, mode="aggregate", work=None, output=None, plan_path=None):
        self.study = Path(study).resolve()
        self.mode = mode
        if mode not in ("aggregate", "full", "synthetic"):
            raise StudyError("Mode must be aggregate, full or synthetic")
        self.output = Path(output or self.study / "work/notebook-analysis" / mode).resolve()
        # Outputs may never land in published historical evidence/results or input data.
        for protected in (self.study / "results", self.study / "evidence", self.study / "notebooks"):
            if self.output == protected or protected in self.output.parents:
                raise StudyError("Use a new output directory outside historical/notebook sources")
        self.plan = validate_plan(read_json(plan_path or self.study / "configs/analysis-time-bands-v2.json"))
        self.plan_sha = freeze_plan(self.output / "plan.json", self.plan)  # BEFORE observations
        self.historical = self._historical()
        self.rows = None
        self.verification = None
        self.blocked = ["Snapshot bruto, features/labels/audit e modelos completos não fornecidos; reanálise real indisponível."]
        self.source = {"data_kind": "real_retained_aggregates", "snapshot_sha256": self.historical["sample"]["sampling"]["snapshot_sha256"],
                       "data_protocol_version": "sequential-v1", "eligibility_version": "origin-20200701-v1",
                       "sample": {k:self.historical["sample"]["funnel"][k] for k in
                                  ("observed_attempts","excluded","valid_events","valid_censored")},
                       "full_snapshot_recovered": False, "historical_files_sha256": self.historical["checksums"]}
        self.source["historical_snapshot_recovered"] = False
        if mode != "aggregate":
            if not work:
                raise StudyError("Explicit work path required; no auto-discovery or collection")
            root = Path(work).resolve()
            if self.output == root or root in self.output.parents or self.output in root.parents:
                raise StudyError("Analysis outputs and snapshot inputs must be separate")
            self.verification = verify_snapshot(root)
            self.prepared, self.splits, self.rows = load_attempts(root)
            kind = self.prepared["data_kind"]
            if kind != ("real" if mode == "full" else "synthetic"):
                raise StudyError("Data kind does not match requested mode")
            original = self.historical["sample"]
            if self.verification["snapshot_sha256"] == original["sampling"]["snapshot_sha256"]:
                # Independently compare files against retained historical evidence.
                for name, sha in original["reproduction"]["files_sha256"].items():
                    if name in ("prepared/features.jsonl", "prepared/labels.jsonl", "prepared/audit.jsonl", "prepared/manifest.json", "splits.json", "baseline/model.json", "aft/model.json"):
                        if file_sha(root / name) != sha:
                            raise StudyError("Historical reproduction file checksum mismatch: " + name)
            self.blocked = []
            self.source = {"data_kind": kind, **self.verification, "full_snapshot_recovered": kind == "real",
                           "historical_snapshot_recovered": self.verification["snapshot_sha256"] == original["sampling"]["snapshot_sha256"],
                           "data_protocol_version": self.prepared["config"]["protocol_version"],
                           "eligibility_version": self.prepared["config"]["eligibility_version"],
                           "sample": {k:self.prepared[k] for k in
                                      ("observed_attempts","excluded","valid_events","valid_censored")},
                           "prepared_sha256": digest(self.prepared), "splits_sha256": digest(self.splits),
                           "audit_file_sha256": file_sha(root / "prepared/audit.jsonl")}
        previous = self.output / "inputs.json"
        if previous.exists() and read_json(previous)["source"] != self.source:
            raise StudyError("Analysis inputs frozen; use a new output directory for a new snapshot")
        self.export("inputs", {"blocked": self.blocked, "verification": self.verification,
                               "new_predictive_evaluation": "not_performed"})

    @classmethod
    def from_environment(cls, study=None):
        return cls(study or study_directory(), mode=os.environ.get("LOREM_STUDY_MODE", "aggregate"),
                   work=os.environ.get("LOREM_STUDY_WORK"), output=os.environ.get("LOREM_STUDY_OUTPUT"),
                   plan_path=os.environ.get("LOREM_STUDY_PLAN"))

    def _historical(self):
        report = self.study / "results/2026-10-18/report"
        manifest = read_json(report / "manifest.json")
        checks = {}
        for name, sha in manifest["files"].items():
            if file_sha(report / name) != sha:
                raise StudyError("Retained report checksum mismatch: " + name)
            checks["report/" + name] = sha
        evaluation = self.study / "results/2026-10-18/evaluation/metrics.json"
        metrics = read_json(evaluation)
        if digest(metrics) != manifest["evaluation_sha256"]:
            raise StudyError("Historical evaluation checksum mismatch")
        sample_path = self.study / "evidence/2026-10-18-sample-aft.json"
        checks["sample_evidence"] = file_sha(sample_path)
        checks["evaluation_metrics"] = file_sha(evaluation)
        with (report / "metrics.csv").open(encoding="utf-8", newline="") as source:
            selected = [r for r in csv.DictReader(source) if r["metric"] in
                        ("time/mae", "time/rmse", "distribution/integrated_brier_ipcw")]
        return {"sample": read_json(sample_path), "demo": read_json(report / "demo.json"),
                "metrics": metrics, "comparison": selected, "checksums": checks}

    def export(self, name, value):
        envelope = {"schema_version": 1, "analysis_version": self.plan["analysis_version"],
                    "protocol_version": self.plan["protocol_version"], "plan_sha256": self.plan_sha,
                    "source": self.source, "grouping": {k: self.plan[k] for k in
                       ("old_rating_edges", "problem_rating_edges", "bin_convention", "scope", "display_support")},
                    "unit": "seconds in observations; minutes in quantiles/budgets", "estimator": "Kaplan-Meier",
                    "horizons_minutes": self.plan["horizons_minutes"],
                    "budget_minutes": value.get("budget_minutes", self.plan["budget_minutes"]),
                    "payload": value}
        write_json(self.output / f"{name}.json", envelope)
        return envelope

    def overview(self):
        return {"mode": self.mode, "source": self.source, "plan_sha256": self.plan_sha,
                "input_status": "ready" if self.rows is not None else "aggregates_only",
                "output_directory": str(self.output), "blocked": self.blocked,
                "new_predictive_evaluation": "not_performed; existing test examined"}
