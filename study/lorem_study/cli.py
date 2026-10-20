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
    parser.add_argument("command", choices=("fixture", "collect", "prepare", "split", "train-baseline", "predict", "train", "evaluate", "report", "prepare-ipsums", "evaluate-ipsums", "export-time-curves", "lookup-time-curves"))
    parser.add_argument("--work", type=Path, default=Path("work/default"))
    parser.add_argument("--config", type=Path)
    parser.add_argument("--pilot", action="store_true", help="Freeze a two-contest real pilot")
    parser.add_argument("--offline", action="store_true", help="Use only checksum-verified cached responses")
    parser.add_argument("--contests", type=int, default=10, help="Number of synthetic fixture contests")
    parser.add_argument("--old-rating", type=int)
    parser.add_argument("--problem-rating", type=int)
    parser.add_argument("--budgets", type=float, nargs="+")
    parser.add_argument("--model", choices=("baseline", "aft"), default="baseline")
    parser.add_argument("--inputs", type=Path, nargs="*", default=[], help="Explicit local Ipsum exports; no upload")
    parser.add_argument("--source-work", type=Path, help="Frozen contest models/evaluation used by evaluate-ipsums")
    parser.add_argument("--artifact", type=Path, help="Time curve output/input JSON")
    parser.add_argument("--retained-demo", type=Path, help="Export only the exact retained query")
    args = parser.parse_args(argv)
    try:
        if args.command == "evaluate":
            try:
                from .evaluate import evaluate
            except ImportError as error:
                raise StudyError("Evaluation requires optional dependencies: pip install -r requirements-model.lock") from error
        if args.command == "train" or (args.command == "predict" and args.model == "aft"):
            try:
                from .aft import predict_aft, train_aft
            except ImportError as error:
                raise StudyError("AFT requires optional dependencies: pip install -r requirements-model.lock") from error
        if args.command in ("export-time-curves", "lookup-time-curves"):
            from .time_curves import export_curves, retained_demo, lookup
            if args.artifact is None:
                raise StudyError("Time curves require --artifact")
            if args.command == "export-time-curves":
                result = (retained_demo(read_json(args.retained_demo)) if args.retained_demo else
                          export_curves(read_json(args.work / "baseline" / "model.json")))
                write_json(args.artifact, result)
            else:
                result = lookup(read_json(args.artifact), args.old_rating, args.problem_rating)
        elif args.command == "fixture":
            result = create_fixture(args.work, load_config(args.config), args.contests)
        elif args.command == "predict":
            if args.old_rating is None or args.problem_rating is None:
                raise StudyError("predict requires --old-rating and --problem-rating")
            predictor = predict_aft if args.model == "aft" else predict
            result = predictor(read_json(args.work / args.model / "model.json"), args.old_rating,
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
                elif args.command == "train":
                    result = train_aft(args.work)
                elif args.command == "evaluate":
                    result = evaluate(args.work)
                elif args.command == "prepare-ipsums":
                    from .ipsum import prepare_ipsums
                    result = prepare_ipsums(args.work, args.inputs, offline=args.offline)
                elif args.command == "evaluate-ipsums":
                    from .transport import evaluate_ipsums
                    if args.source_work is None:
                        raise StudyError("evaluate-ipsums requires --source-work with frozen contest models")
                    result = evaluate_ipsums(args.work, args.source_work)
                else:
                    from .report import report
                    try:
                        result = report(args.work)
                    except ImportError as error:
                        raise StudyError("Report requires optional dependencies: pip install -r requirements-report.lock") from error
        print(json.dumps(result, ensure_ascii=False, allow_nan=False, sort_keys=True))
        return 0
    except (StudyError, OSError, KeyError, TypeError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2
