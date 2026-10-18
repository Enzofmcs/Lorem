from datetime import datetime
from pathlib import Path
import math

from .io import StudyError, check_version, read_json

DEFAULT_PATH = Path(__file__).with_name("default.json")


def load_config(path=None):
    config = read_json(path or DEFAULT_PATH)
    check_version(config)
    if config.get("cutoff_utc") != "2020-07-01T00:00:00Z":
        raise StudyError("The approved cutoff is 2020-07-01T00:00:00Z")
    if config.get("protocol_version") != "sequential-v1":
        raise StudyError("Unsupported protocol_version")
    if config.get("eligibility_version") != "origin-20200701-v1":
        raise StudyError("Unsupported eligibility_version")
    if config.get("selection_policy") not in (None, "regular-simple-v1"):
        raise StudyError("Unsupported selection_policy")
    if "max_screened_candidates" in config and (
        type(config["max_screened_candidates"]) is not int or config["max_screened_candidates"] < config["target_contests"]
    ):
        raise StudyError("Invalid max_screened_candidates")
    for field in ("seed", "target_contests", "pilot_contests", "minimum_contests",
                  "page_size", "max_pages", "max_retries", "pending_rechecks",
                  "delta_bin_width", "bin_min_attempts", "bin_min_events"):
        value = config.get(field)
        if type(value) is not int or value < (0 if field in ("seed", "max_retries", "pending_rechecks") else 1):
            raise StudyError(f"Invalid {field}")
    if config["minimum_contests"] < 10 or config["pilot_contests"] != 2:
        raise StudyError("At least 10 contests for evaluation; pilot must contain 2")
    if config["target_contests"] < config["minimum_contests"]:
        raise StudyError("Target smaller than minimum_contests")
    for field in ("request_interval_seconds", "timeout_seconds"):
        value = config.get(field)
        if type(value) not in (int, float) or not math.isfinite(value) or value <= 0:
            raise StudyError(f"Invalid {field}")
    if config["request_interval_seconds"] < 2:
        raise StudyError("Codeforces gate must be at least 2 seconds")
    budgets = config.get("budgets_minutes")
    if not isinstance(budgets, list) or not budgets or any(
        type(b) not in (int, float) or not math.isfinite(b) or b <= 0 for b in budgets
    ) or budgets != sorted(set(budgets)):
        raise StudyError("budgets_minutes must be positive, finite, unique and sorted")
    groups = config.get("shared_contest_groups")
    if not isinstance(groups, list) or any(
        not isinstance(g, dict) or not g.get("source") or
        not isinstance(g.get("contest_ids"), list) or len(g["contest_ids"]) < 2 or
        any(type(i) is not int or i <= 0 for i in g["contest_ids"]) for g in groups
    ):
        raise StudyError("Shared groups require contest_ids and a verifiable source")
    return config


def cutoff_seconds(config):
    return int(datetime.fromisoformat(config["cutoff_utc"].replace("Z", "+00:00")).timestamp())
