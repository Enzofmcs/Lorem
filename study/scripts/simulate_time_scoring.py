"""Run from installed lorem-study or with PYTHONPATH=study; offline, no data fitting."""
from pathlib import Path
from lorem_study.io import write_json
from lorem_study.time_simulation import simulate

if __name__ == "__main__":
    root = Path(__file__).resolve().parents[2]
    write_json(root / "study/results/time-scoring-v1/scenarios.json", simulate())
