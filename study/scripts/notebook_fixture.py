"""Create an isolated SYNTHETIC smoke snapshot using the actual existing pipeline."""
import argparse
from pathlib import Path
from lorem_study.fixture import create_fixture
from lorem_study.config import load_config
from lorem_study.prepare import prepare
from lorem_study.baseline import split, train_baseline
from lorem_study.aft import train_aft
from lorem_study.io import read_json, write_json

if __name__ == "__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("destination",type=Path)
    args=parser.parse_args()
    create_fixture(args.destination,load_config())
    prepare(args.destination)
    split(args.destination)
    train_baseline(args.destination)
    train_aft(args.destination)
    plan = read_json(Path(__file__).resolve().parents[1] / "configs/analysis-time-bands-v2.json")
    plan.update(display_support={"attempts":5,"events":2,"groups":2}, software_smoke_only=True)
    write_json(args.destination / "smoke-plan.json", plan)
    print("SYNTHETIC ONLY: notebook software smoke, not empirical evidence")
