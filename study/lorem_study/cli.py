import argparse
from contextlib import contextmanager
import json
from pathlib import Path
import sys
import os

from .baseline import predict, split, train_baseline
from .collect import collect
from .config import load_config
from .fixture import create_fixture
from .io import StudyError, read_json, write_json
from .prepare import prepare


@contextmanager
def work_lock(root):
    root = Path(root)
    root.mkdir(parents=True, exist_ok=True)
    with (root / ".lock").open("a+b") as stream:
        if stream.tell() == 0:
            stream.write(b"0")
            stream.flush()
        stream.seek(0)
        try:
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as error:
            raise StudyError("Another command is writing this work directory") from error
        try:
            yield
        finally:
            stream.seek(0)
            if os.name == "nt":
                msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(stream, fcntl.LOCK_UN)


def main(argv=None):
    parser = argparse.ArgumentParser(description="Lorem quantitative study; synthetic data are not empirical results")
    parser.add_argument("command", choices=("fixture", "collect", "prepare", "split", "train-baseline", "predict", "train", "evaluate", "report"))
    parser.add_argument("--work", type=Path, default=Path("work/default"))
    parser.add_argument("--config", type=Path)
    parser.add_argument("--pilot", action="store_true", help="Freeze a two-contest real pilot")
    parser.add_argument("--offline", action="store_true", help="Use only checksum-verified cached responses")
    parser.add_argument("--contests", type=int, default=10, help="Number of synthetic fixture contests")
    parser.add_argument("--old-rating", type=int)
    parser.add_argument("--problem-rating", type=int)
    parser.add_argument("--budgets", type=float, nargs="+")
    args = parser.parse_args(argv)
    try:
        if args.command in ("train", "evaluate"):
            raise StudyError("NOT IMPLEMENTED: AFT training/evaluation belong to Histories 26/27; no research metrics produced")
        if args.command == "fixture":
            result = create_fixture(args.work, load_config(args.config), args.contests)
        elif args.command == "predict":
            if args.old_rating is None or args.problem_rating is None:
                raise StudyError("predict requires --old-rating and --problem-rating")
            result = predict(read_json(args.work / "baseline" / "model.json"), args.old_rating,
                             args.problem_rating, args.budgets or [30, 60, 120])
        else:
            with work_lock(args.work):
                if args.command == "collect":
                    result = collect(args.work, load_config(args.config), pilot=args.pilot, offline=args.offline)
                elif args.command == "prepare":
                    result = prepare(args.work)
                elif args.command == "split":
                    result = split(args.work)
                elif args.command == "train-baseline":
                    result = train_baseline(args.work)
                else:
                    prepared = args.work / "prepared" / "manifest.json"
                    result = {"schema_version": 1, "status": "pipeline_status_only; full report belongs to History 28",
                              "data_kind": read_json(prepared)["data_kind"] if prepared.exists() else "not_collected",
                              "stages": {stage: path.exists() for stage, path in {
                                  "snapshot": args.work / "snapshot.json", "prepared": prepared,
                                  "split": args.work / "splits.json", "baseline": args.work / "baseline" / "model.json"}.items()},
                              "research_metrics": "NOT EXECUTED (History 27)"}
                    write_json(args.work / "status.json", result)
        print(json.dumps(result, ensure_ascii=False, allow_nan=False, sort_keys=True))
        return 0
    except (StudyError, OSError, KeyError, TypeError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2
