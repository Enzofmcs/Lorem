"""Execute notebooks in separate clean kernels, offline, and export standalone HTML.

Use the interpreter from the notebook venv. Source notebooks are never overwritten.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys

import nbformat
from nbclient import NotebookClient
from nbconvert import HTMLExporter
from jupyter_client.kernelspec import KernelSpecManager


def run(study, output, mode, work=None, plan=None):
    study, output = study.resolve(), output.resolve()
    if output.exists() and any(output.iterdir()):
        raise ValueError("Use an empty output directory for a reviewable run")
    protected = [study / name for name in ("results", "evidence", "notebooks")]
    if any(output == p or p in output.parents for p in protected):
        raise ValueError("Do not overwrite historical/notebook source directories")
    output.mkdir(parents=True, exist_ok=True)
    # An isolated kernelspec binds execution to THIS interpreter, on Windows/Linux.
    spec = output / "kernel" / "lorem-study"
    spec.mkdir(parents=True)
    (spec / "kernel.json").write_text(json.dumps({"argv": [sys.executable, "-m", "ipykernel_launcher", "-f", "{connection_file}"],
         "display_name": "Lorem study (isolated)", "language": "python"}), encoding="utf-8")
    manager = KernelSpecManager(kernel_dirs=[str(spec.parent)])
    env = {**os.environ, "LOREM_STUDY_MODE": mode, "LOREM_STUDY_OUTPUT": str(output / "analysis"),
           "MPLCONFIGDIR": str(output / "matplotlib"), "IPYTHONDIR": str(output / "ipython"),
           "JUPYTER_RUNTIME_DIR": str(output / "jupyter"), "LOREM_STUDY_WORK": str(work.resolve()) if work else "",
           "XDG_CACHE_HOME": str(output / "cache"),
           "LOREM_STUDY_PLAN": str(plan.resolve()) if plan else ""}
    manifests = []
    for path in sorted((study / "notebooks").glob("0[0-4]_*.ipynb")):
        notebook = nbformat.read(path, as_version=4)
        client = NotebookClient(notebook, timeout=900, kernel_name="lorem-study", allow_errors=False,
                                resources={"metadata": {"path": str(study)}},
                                kernel_manager_class=__import__('jupyter_client').AsyncKernelManager)
        client.create_kernel_manager()
        client.km.kernel_spec_manager = manager
        client.execute(env=env)
        target = output / path.name
        nbformat.write(notebook, target)
        exporter = HTMLExporter(template_name="lab", embed_images=True)
        html, _ = exporter.from_notebook_node(notebook)
        html_path = target.with_suffix(".html")
        html_path.write_text(html, encoding="utf-8")
        manifests.append({"notebook": path.name, "source_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                          "executed_sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
                          "html_sha256": hashlib.sha256(html_path.read_bytes()).hexdigest(),
                          "executed_cells": sum(c.cell_type == "code" for c in notebook.cells)})
        print(json.dumps(manifests[-1]), flush=True)
    if len(manifests) != 5:
        raise ValueError("Expected five notebooks")
    (output / "execution.json").write_text(json.dumps({"mode": mode, "clean_kernels": 5,
       "network": "HTTP API disabled in notebook bootstrap; no collection/training", "files": manifests}, indent=2)+"\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--mode", choices=["aggregate", "full", "synthetic"], default="aggregate")
    parser.add_argument("--work", type=Path)
    parser.add_argument("--plan", type=Path)
    args = parser.parse_args()
    run(Path(__file__).resolve().parents[1], args.output, args.mode, args.work, args.plan)
