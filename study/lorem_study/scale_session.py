"""Offline presentation versus explicit full reanalysis; acquisition is a CLI step."""

from pathlib import Path

from .io import StudyError, digest, read_json, write_json
from .scale_analysis import FrozenModels, analyze_scale, export_table, validate_plan
from .scale_prepare import file_sha
from .scale_view import export_figures, interactive_html


def verify_delivery(root):
    root=Path(root).resolve()
    manifest=read_json(root/"delivery-manifest.json")
    for name,sha in manifest["files_sha256"].items():
        path=(root/name).resolve()
        if root not in path.parents or not path.is_file() or file_sha(path)!=sha:
            raise StudyError(f"Delivery file missing, unsafe or corrupt: {name}")
    return manifest


class ScaleSession:
    def __init__(self,study,*,mode="aggregate",results=None,full_root=None,output=None):
        self.study=Path(study).resolve()
        self.results=Path(results or self.study/"results/scale-t50-v1").resolve()
        self.output=Path(output or self.study/"work/scale-notebook"/mode).resolve()
        self.mode=mode
        self.full_root=Path(full_root).resolve() if full_root else None
        if mode not in ("aggregate","full"):
            raise StudyError("Scale modes: aggregate or full; synthetic inputs belong in isolated tests")
        protected=[self.results,self.study/"results",self.study/"notebooks",self.study/"evidence"]
        if self.full_root:protected.append(self.full_root)
        if any(self.output==p or p in self.output.parents or self.output in p.parents for p in protected):
            raise StudyError("Choose a separate output directory, outside protected data/sources")
        self.manifest=verify_delivery(self.results)
        self.report=read_json(self.results/"analysis.json")
        if self.report.get("data_kind")!="real":
            raise StudyError("Presentation expects retained real results; fixtures remain isolated in tests")
        self.plan=validate_plan(self.report["plan"])
        if digest(self.plan)!=self.report["plan_sha256"]:
            raise StudyError("Analysis/plan mismatch")
        self.models=FrozenModels(self.results/"models",self.plan["model_inventory"])
        if {a["data_kind"] for a in self.models.artifacts.values()}!={self.report["data_kind"]} or self.models.hashes!=self.report["model_hashes"]:
            raise StudyError("Presentation/model provenance mismatch")
        self.fixed=read_json(self.results/"fixed-queries.json")
        self.output.mkdir(parents=True,exist_ok=True)
        self.source={"mode":mode,"data_kind":self.report["data_kind"],
                     "snapshot_sha256":self.report["snapshot_sha256"],"plan_sha256":self.report["plan_sha256"],
                     "prepared_contests":len({i for g in self.report["groups"] for i in g}),"groups":len(self.report["groups"]),
                     "eligible_contests":len(self.report["eligible_contest_ids"]),
                     "valid_attempts":self.report["total_valid"],
                     "reanalyzed_observations":False,"network_requests":0,
                     "note":"Retained real aggregate results; no new joins/bootstrap in presentation mode"}
        if mode=="full":
            if not self.full_root:
                raise StudyError("Full mode requires explicit LOREM_SCALE_FULL prepared directory")
            prepared=read_json(self.full_root/"manifest.json")
            if prepared["snapshot_sha256"]!=self.report["snapshot_sha256"]:
                raise StudyError("Full snapshot differs from retained presentation")
            if not prepared.get("database_sha256") or file_sha(self.full_root/"attempts.sqlite")!=prepared["database_sha256"]:
                raise StudyError("Full analytic database checksum mismatch")
        write_json(self.output/"source.json",self.source)

    def reanalyze(self):
        if self.mode!="full":
            raise StudyError("Aggregate mode has no observations and cannot redo bootstrap/joins")
        from .cli import work_lock
        with work_lock(self.full_root):
            self.report=analyze_scale(self.full_root/"attempts.sqlite",read_json(self.full_root/"manifest.json"),
                                      self.plan,self.models,self.output/"reanalyzed")
        self.fixed=read_json(self.output/"reanalyzed/fixed-queries.json")
        self.source.update(reanalyzed_observations=True,note="On-disk observations reanalyzed; frozen models; no network/training")
        write_json(self.output/"source.json",self.source)
        return self.report

    def export(self):
        write_json(self.output/"analysis.json",self.report)
        export_table(self.report,self.output/"t50.csv")
        export_figures(self.report,self.output/"figures")
        (self.output/"dashboard.html").write_text(interactive_html(self.report,self.fixed),encoding="utf-8")
        return self.output

    def fixed_query(self,old_rating,problem_ratings):
        """An exact model scenario; never relabelled as an observed band mixture."""
        return self.models.fixed(old_rating,problem_ratings)
