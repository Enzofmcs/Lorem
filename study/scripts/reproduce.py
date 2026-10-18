"""Rebuild the frozen study in an empty destination using an installed package.

Usage: /fresh/venv/bin/python scripts/reproduce.py SOURCE DESTINATION
Raw objects are immutable hardlinks when possible; mutable pointers are copied.
"""
import argparse
import hashlib
from importlib.metadata import version
import json
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import time


def link_or_copy(source, destination):
    try:
        Path(destination).hardlink_to(source)
    except OSError:
        shutil.copy2(source, destination)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    source, destination = args.source.resolve(), args.destination.resolve()
    if destination.exists():
        parser.error("Destination must not exist; preserve frozen runs")
    destination.mkdir(parents=True)
    for name in ("raw", "responses", "screenings", "snapshots"):
        if (source / name).exists():
            shutil.copytree(source / name, destination / name, copy_function=link_or_copy)
    for name in ("cache.json", "screening.json", "selection.json", "snapshot.json"):
        if (source / name).exists():
            shutil.copy2(source / name, destination / name)
    config = Path(__file__).resolve().parents[1] / "configs" / "regular-simple-v1.json"
    commands = [("collect", "--offline", "--config", str(config)), ("prepare",), ("split",),
                ("train-baseline",), ("train",), ("evaluate",), ("report",)]
    runs = []
    for command in commands:
        start = time.monotonic()
        result = subprocess.run([sys.executable, "-m", "lorem_study", *command, "--work", str(destination)],
                                capture_output=True, text=True, cwd=destination)
        log = {"stage": command[0], "seconds": round(time.monotonic() - start, 3), "exit_code": result.returncode,
               "stderr": result.stderr}
        if command[0] == "collect" and result.returncode == 0:
            log["result"] = json.loads(result.stdout)
        runs.append(log)
        print(json.dumps(log), flush=True)
        if result.returncode:
            (destination / "failed-stage.log").write_text(result.stdout + result.stderr)
            raise SystemExit(result.returncode)
    files = ["prepared/" + n for n in ("features.jsonl", "labels.jsonl", "audit.jsonl", "manifest.json")]
    files += ["splits.json"] + [f"{stage}/{name}" for stage in ("baseline", "aft")
                               for name in ("model.json", "predictions.jsonl", "manifest.json")]
    files += ["evaluation/" + n for n in ("plan.json", "metrics.json", "manifest.json")]
    files += ["report/" + p.name for p in sorted((source / "report").iterdir()) if p.is_file()]
    checks = {}
    for name in files:
        original, rebuilt = source / name, destination / name
        sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
        checks[name] = {"sha256": sha(rebuilt), "identical": sha(original) == sha(rebuilt)}
    evidence = {"python": platform.python_version(), "packages": {k: version(k) for k in
                ("lorem-study", "numpy", "scipy", "matplotlib", "contourpy")},
                "commands": runs, "files": checks, "all_identical": all(v["identical"] for v in checks.values())}
    (destination / "reproduction.json").write_text(json.dumps(evidence, indent=2) + "\n")
    print(json.dumps({"all_identical": evidence["all_identical"], "files_compared": len(checks)}))
    return 0 if evidence["all_identical"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
