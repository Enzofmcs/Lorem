"""Grouped splits and empirical survival references, fitted on training only."""

from collections import defaultdict
import math
from pathlib import Path

from .io import StudyError, check_version, digest, read_json, read_rows, write_json, write_rows


def split(root):
    root = Path(root)
    prepared = read_json(root / "prepared" / "manifest.json")
    check_version(prepared)
    ids = prepared["complete_contest_ids"]
    if len(ids) < prepared["config"]["minimum_contests"]:
        raise StudyError(f"Insufficient complete contests ({len(ids)}); pilot only, no evaluation split")
    parent = {i: i for i in ids}

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    # Include external origin nodes as bridges between selected contests.
    for link in prepared["origin_links"]:
        nodes = link["contest_ids"]
        for i in nodes:
            parent.setdefault(i, i)
        for i in nodes[1:]:
            parent[find(i)] = find(nodes[0])
    groups = defaultdict(list)
    for i in ids:
        groups[find(i)].append(i)
    ordered = sorted((sorted(v) for v in groups.values()),
                     key=lambda v: digest([prepared["config"]["seed"], v]))
    # Small bounded DP partitions whole groups by count, preserving at least 6/2/2.
    # Its inputs are contest identities and verified links, never outcomes or row counts.
    states = {(0, 0): ()}
    total = 0
    for group in ordered:
        size = len(group)
        following = {}
        for (train, val), assignment in states.items():
            for part, state in (("train", (train + size, val)),
                                ("validation", (train, val + size)), ("test", (train, val))):
                candidate = assignment + (part,)
                if state not in following or candidate < following[state]:
                    following[state] = candidate
        states = following
        total += size
    valid = [(abs(t - .6 * total) + abs(v - .2 * total) + abs(total - t - v - .2 * total), a)
             for (t, v), a in states.items() if t >= 6 and v >= 2 and total - t - v >= 2]
    if not valid:
        raise StudyError("Shared groups cannot satisfy independent 6/2/2 partitions")
    assignment = min(valid)[1]
    partitions = {name: [] for name in ("train", "validation", "test")}
    for group, part in zip(ordered, assignment):
        partitions[part].extend(group)
    for values in partitions.values():
        values.sort()
    manifest = {"schema_version": 1, "data_kind": prepared["data_kind"],
                "seed": prepared["config"]["seed"], "prepared_sha256": digest(prepared),
                "method": "seeded whole-contest groups; 60/20/20 nearest feasible counts",
                "groups": ordered, "partitions": partitions}
    path = root / "splits.json"
    if path.exists() and read_json(path) != manifest:
        raise StudyError("Split already frozen for another dataset; use a new work directory")
    write_json(path, manifest)
    return manifest


def km_fit(observations, *, reverse=False):
    """Product-limit; events at a tie precede censors; reverse KM uses n-d for c/n."""
    counts = defaultdict(lambda: [0, 0])
    for duration, event in observations:
        if (type(duration) not in (int, float) or not math.isfinite(duration) or duration <= 0 or
            type(event) is not int or event not in (0, 1)):
            raise StudyError("KM requires finite positive duration and event 0/1")
        counts[duration][0 if event else 1] += 1
    if not counts:
        raise StudyError("No usable training observations")
    at_risk = sum(sum(v) for v in counts.values())
    n, survival, table = at_risk, 1.0, []
    for duration, (events, censored) in sorted(counts.items()):
        denominator = at_risk - events if reverse else at_risk
        failures = censored if reverse else events
        if denominator:
            survival *= 1 - failures / denominator
        table.append({"seconds": duration, "at_risk": at_risk, "events": events,
                      "censored": censored, "survival": survival})
        at_risk -= events + censored
    return {"n": n, "events": sum(v[0] for v in counts.values()), "table": table,
            "max_followup_seconds": max(counts), "reverse_censoring": reverse}


def km_probability(curve, seconds):
    if type(seconds) not in (int, float) or not math.isfinite(seconds) or seconds < 0:
        raise StudyError("Invalid prediction horizon")
    survival = 1.0
    for row in curve["table"]:
        if row["seconds"] > seconds:
            break
        survival = row["survival"]
    if seconds > curve["max_followup_seconds"] and survival > 1e-12:
        return None
    return max(0.0, min(1.0, 1 - survival))


def km_quantile(curve, q):
    for row in curve["table"]:
        if 1 - row["survival"] >= q - 1e-12:
            return row["seconds"] / 60
    return None


def expected_d012_minutes(old_rating, problem_rating):
    # Kotlin roundToInt for nonnegative values, not Python's round-to-even.
    milliseconds = math.floor(min(120, max(30, 60 + (problem_rating - old_rating) / 10)) * 60000 + .5)
    return milliseconds / 60000


def train_baseline(root):
    root = Path(root)
    prepared = read_json(root / "prepared" / "manifest.json")
    splits = read_json(root / "splits.json")
    check_version(splits)
    if splits["prepared_sha256"] != digest(prepared):
        raise StudyError("Prepared dataset changed after split freeze")
    features = list(read_rows(root / "prepared" / "features.jsonl"))
    labels = list(read_rows(root / "prepared" / "labels.jsonl"))
    if digest(features) != prepared["features_sha256"] or digest(labels) != prepared["labels_sha256"]:
        raise StudyError("Prepared table checksum mismatch")
    partitions = splits["partitions"]
    flat = [i for ids in partitions.values() for i in ids]
    if len(flat) != len(set(flat)) or set(flat) != set(prepared["complete_contest_ids"]):
        raise StudyError("Leaking/missing contest partition")
    for group in splits["groups"]:
        if sum(bool(set(group) & set(ids)) for ids in partitions.values()) != 1:
            raise StudyError("Shared group leaks between partitions")
    train_ids = set(partitions["train"])
    by_id = {f["sample_id"]: f for f in features}
    if len(by_id) != len(features) or len({r["sample_id"] for r in labels}) != len(labels):
        raise StudyError("Duplicate sample identities")
    train = []
    for label in labels:
        if label["contest_id"] in train_ids and label["quality"] == "valid":
            f = by_id.get(label["sample_id"])
            if (not f or f["contest_id"] != label["contest_id"] or
                any(type(f.get(k)) is not int for k in ("old_rating", "problem_rating", "delta")) or
                f["delta"] != f["problem_rating"] - f["old_rating"]):
                raise StudyError("Invalid training feature/label join")
            train.append((f, label))
    config = prepared["config"]
    observations = [(r["duration_seconds"], r["event"]) for _, r in train]
    global_curve = km_fit(observations)
    buckets = defaultdict(list)
    for f, r in train:
        buckets[str((f["delta"] // config["delta_bin_width"]) * config["delta_bin_width"])].append((r["duration_seconds"], r["event"]))
    curves = {k: km_fit(v) for k, v in buckets.items()
              if len(v) >= config["bin_min_attempts"] and sum(e for _, e in v) >= config["bin_min_events"]}
    artifact = {"schema_version": 1, "model_version": "kaplan-meier-v1", "data_kind": prepared["data_kind"],
                "protocol_version": config["protocol_version"], "eligibility_version": config["eligibility_version"],
                "training_contest_ids": sorted(train_ids), "training_sha256": digest(train),
                "seed": config["seed"], "global": global_curve, "delta_bins": curves,
                "delta_bin_width": config["delta_bin_width"], "bin_min_attempts": config["bin_min_attempts"],
                "bin_min_events": config["bin_min_events"], "censoring_train_only": km_fit(observations, reverse=True),
                "domain": {k: [min(f[k] for f, _ in train), max(f[k] for f, _ in train)]
                           for k in ("old_rating", "problem_rating", "delta")}}
    output = root / "baseline"
    archived = output / "artifacts" / f"{digest(artifact)}.json"
    if not archived.exists():
        write_json(archived, artifact)
    write_json(output / "model.json", artifact)
    # Prediction exports contain no labels, ranks, scores or realized durations.
    part_for = {i: p for p, ids in partitions.items() for i in ids}
    predictions = []
    for f in features:
        if f["contest_id"] in part_for and type(f["old_rating"]) is int and f["old_rating"] > 0 and type(f["problem_rating"]) is int:
            predictions.append({"sample_id": f["sample_id"], "partition": part_for[f["contest_id"]],
                                **predict(artifact, f["old_rating"], f["problem_rating"], config["budgets_minutes"])})
    write_rows(output / "predictions.jsonl", predictions)
    write_json(output / "manifest.json", {"schema_version": 1, "data_kind": artifact["data_kind"],
                                         "artifact_sha256": digest(artifact), "prepared_sha256": digest(prepared),
                                         "splits_sha256": digest(splits), "predictions_sha256": digest(predictions),
                                         "status": "predictions_only; evaluation not executed"})
    return artifact


def predict(artifact, old_rating, problem_rating, budgets):
    check_version(artifact)
    if artifact.get("model_version") != "kaplan-meier-v1":
        raise StudyError("Unsupported model version")
    if any(type(v) is not int or v <= 0 for v in (old_rating, problem_rating)):
        raise StudyError("Ratings must be positive integers with prior history")
    if not budgets or any(type(v) not in (int, float) or not math.isfinite(v) or v <= 0 for v in budgets):
        raise StudyError("Budgets must be finite and positive")
    delta = problem_rating - old_rating
    bucket = str((delta // artifact["delta_bin_width"]) * artifact["delta_bin_width"])
    curve = artifact["delta_bins"].get(bucket, artifact["global"])
    domain_values = {"old_rating": old_rating, "problem_rating": problem_rating, "delta": delta}
    outside = [k for k, v in domain_values.items() if not artifact["domain"][k][0] <= v <= artifact["domain"][k][1]]
    return {"data_kind": artifact["data_kind"], "model_version": artifact["model_version"],
            "artifact_sha256": digest(artifact),
            "reference": "delta_bin" if bucket in artifact["delta_bins"] else "global_fallback",
            "out_of_domain": outside, "training_attempts": curve["n"],
            "probabilities": [{"budget_minutes": b, "p_ac": km_probability(curve, b * 60),
                               "support": "extrapolation" if outside else ("available" if km_probability(curve, b * 60) is not None else "insufficient_followup")}
                              for b in budgets],
            "median_minutes": km_quantile(curve, .5),
            "predictive_interval_80_minutes": [km_quantile(curve, .1), km_quantile(curve, .9)],
            "interval_kind": "empirical predictive quantiles, not a confidence interval",
            "d012_expected_minutes": expected_d012_minutes(old_rating, problem_rating),
            "d012_probability": None}
