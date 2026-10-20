"""Offline descriptive analysis for notebooks 44–48. Core remains stdlib only.

No collector, training, imputation, global fallback, or network is invoked here.
Times enter in seconds; displayed quantiles and budgets use minutes.
"""
from collections import Counter, defaultdict
import hashlib
import math
from pathlib import Path
import random

from .baseline import km_fit, km_probability, km_quantile
from .dataset import load_dataset
from .io import StudyError, digest, read_json, read_rows, write_json


ANALYSIS_VERSION = "analysis-time-bands-v2"


def file_sha(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def validate_plan(plan):
    if plan.get("schema_version") != 1 or plan.get("analysis_version") != ANALYSIS_VERSION:
        raise StudyError("Unsupported analysis plan")
    for key in ("old_rating_edges", "problem_rating_edges"):
        edges = plan[key]
        if len(edges) < 2 or any(type(v) is not int or v < 0 for v in edges) or edges != sorted(set(edges)):
            raise StudyError("Invalid rating grid")
    for value in [plan["budget_minutes"], *plan["horizons_minutes"]]:
        positive(value)
    if plan["scope"] not in ("all_descriptive", "train"):
        raise StudyError("Use descriptive snapshot or frozen train only")
    if plan["quantiles"] != [.25, .5, .75] or plan["delta_bin_width"] <= 0:
        raise StudyError("Invalid quartile/delta contract")
    if type(plan["bootstrap_replicates"]) is not int or plan["bootstrap_replicates"] < 2:
        raise StudyError("At least two bootstrap replicates required")
    if any(type(v) is not int or v < 1 for v in plan["display_support"].values()):
        raise StudyError("Invalid support policy")
    return plan


def positive(value):
    if type(value) not in (int, float) or not math.isfinite(value) or value <= 0:
        raise StudyError("Expected finite positive value")


def freeze_plan(path, plan):
    validate_plan(plan)
    if Path(path).exists() and read_json(path) != plan:
        raise StudyError("Analysis plan frozen; use a new output directory")
    write_json(path, plan)
    return digest(plan)


def rating_bin(value, edges):
    if type(value) is not int or value <= 0:
        return None
    return next((lo for lo, hi in zip(edges, edges[1:]) if lo <= value < hi), None)


def delta_bin(value, width=400):
    if type(value) is not int or type(width) is not int or width <= 0:
        raise StudyError("Delta and width must be integers, width positive")
    return value // width * width


def verify_snapshot(root, expected=None):
    """Check exact immutable raw references, prepared snapshot and fitted artifacts."""
    root = Path(root)
    snapshot = read_json(root / "snapshot.json")
    snapshot_sha = digest(snapshot)
    if expected and snapshot_sha != expected:
        raise StudyError("Unexpected snapshot; never substitute a new acquisition for the old one")
    refs = {}

    def visit(value):
        if isinstance(value, dict):
            if "sha256" in value and "url" in value:
                refs[value["sha256"]] = value
            for item in value.values():
                visit(item)
        elif isinstance(value, list):
            for item in value:
                visit(item)
    visit(snapshot)
    if not refs:
        raise StudyError("Snapshot has no immutable raw provenance")
    for sha in refs:
        if len(sha) != 64 or any(c not in "0123456789abcdef" for c in sha):
            raise StudyError("Invalid raw digest")
        if file_sha(root / "raw" / f"{sha}.json") != sha:
            raise StudyError("Raw snapshot checksum mismatch")
    prepared = read_json(root / "prepared/manifest.json")
    if (prepared["snapshot_sha256"] != snapshot_sha or prepared["config"] != snapshot["config"] or
            prepared["data_kind"] != snapshot["data_kind"]):
        raise StudyError("Prepared/snapshot mismatch")
    models = {}
    splits = read_json(root / "splits.json")
    for name in ("baseline", "aft"):
        model = read_json(root / name / "model.json")
        sha = digest(model)
        manifest = read_json(root / name / "manifest.json")
        if (manifest["artifact_sha256"] != sha or manifest["prepared_sha256"] != digest(prepared) or
                manifest["splits_sha256"] != digest(splits) or model["data_kind"] != prepared["data_kind"]):
            raise StudyError("Model checksum/provenance mismatch")
        models[name] = sha
    return {"snapshot_sha256": snapshot_sha, "raw_objects_verified": len(refs), "model_sha256": models}


def load_attempts(root):
    """Strict 1:1 feature/label/audit join, including excluded rows for the funnel.

    The existing preparer owns eligibility; do not recreate it from current ratings.
    """
    root = Path(root)
    prepared, splits, features, partition_rows = load_dataset(root)
    del partition_rows  # Do not retain another copy of all labels in a large snapshot.
    maps = []
    for table in (features, read_rows(root / "prepared/labels.jsonl")):
        by_id = {}
        for item in table:
            if item["sample_id"] in by_id:
                raise StudyError("Duplicate sample_id in analytical join")
            by_id[item["sample_id"]] = item
        maps.append(by_id)
    del features, table
    if maps[0].keys() != maps[1].keys():
        raise StudyError("Feature/label/audit identities mismatch")
    group_for = {cid: digest(group) for group in splits["groups"] for cid in group}
    part_for = {cid: p for p, ids in splits["partitions"].items() for cid in ids}
    rows = []
    seen = set()
    for audit in read_rows(root / "prepared/audit.jsonl"):
        sid = audit["sample_id"]
        if sid in seen:
            raise StudyError("Duplicate sample_id in analytical join")
        seen.add(sid)
        if sid not in maps[0]:
            raise StudyError("Feature/label/audit identities mismatch")
        f, label = maps[0].pop(sid), maps[1].pop(sid)
        if (f["contest_id"] != label["contest_id"] or f["contest_id"] != audit["contest_id"] or
            f["participant_key"] != audit["participant_key"] or f["problem_index"] != audit["problem_index"] or
            any(label.get(k) != audit.get(k) for k in ("quality", "reason")) or
            (label["quality"] == "valid" and any(label.get(k) != audit.get(k) for k in
                ("duration_seconds", "event", "sensitivity_last_submission_seconds")))):
            raise StudyError("Inconsistent analytical join")
        if label["quality"] == "valid":
            duration = label["duration_seconds"]
            positive(duration)
            if (type(label["event"]) is not int or label["event"] not in (0, 1) or
                f["contest_id"] not in group_for or f["old_rating"] <= 0 or f["problem_rating"] <= 0 or
                f["delta"] != f["problem_rating"] - f["old_rating"] or
                audit["observed_end_seconds"] - audit["anchor_seconds"] != duration or
                not audit["start_seconds"] <= audit["anchor_seconds"] < audit["observed_end_seconds"] <= audit["end_seconds"] or
                audit["origin_start_seconds"] < 1593561600 or
                (label["event"] and audit["first_ac_seconds"] != audit["observed_end_seconds"])):
                raise StudyError("Invalid analytical observation/units/eligibility")
        elif label["quality"] != "excluded":
            raise StudyError("Unknown quality")
        rows.append({**f, **label,
                     **{k: audit.get(k) for k in ("anchor_seconds", "start_seconds", "end_seconds", "observed_end_seconds")},
                     "group": group_for.get(f["contest_id"]), "partition": part_for.get(f["contest_id"])})
    if maps[0] or maps[1]:
        raise StudyError("Feature/label/audit identities mismatch")
    counts = funnel(rows)["total"]
    if any(counts[k] != prepared[k] for k in ("observed_attempts", "valid_events", "valid_censored", "excluded")):
        raise StudyError("Analytical totals disagree with prepared manifest")
    return prepared, splits, rows


def funnel(rows, width=400):
    """Disjoint accounting; distinct counts are never summed across strata."""
    def summarize(items):
        valid = [r for r in items if r["quality"] == "valid"]
        return {"observed_attempts": len(items), "excluded": len(items) - len(valid),
                "valid_events": sum(r["event"] for r in valid),
                "valid_censored": sum(1-r["event"] for r in valid),
                "participants": len({r["participant_key"] for r in items}),
                "problems": len({(r["problem_contest_id"], r["problem_index"]) for r in items}),
                "contests": len({r["contest_id"] for r in items}),
                "reasons": dict(Counter(r["reason"] for r in items if r["quality"] != "valid"))}
    result = {"total": summarize(rows)}
    for field in ("contest_id", "old_rating", "problem_rating", "delta"):
        groups = defaultdict(list)
        for row in rows:
            value = row[field]
            key = value if field == "contest_id" else (None if value is None else value // width * width)
            groups[str(key)].append(row)
        result[field] = {key: summarize(values) for key, values in sorted(groups.items())}
    return result


def observations(rows, estimand="sequential"):
    result, missing = [], 0
    for row in rows:
        if row["quality"] != "valid":
            continue
        duration = row["duration_seconds"]
        if estimand == "since_contest_start":
            duration = row["observed_end_seconds"] - row["start_seconds"]
        elif estimand == "last_submission":
            if not row["event"]:
                duration = row["sensitivity_last_submission_seconds"]
        elif estimand != "sequential":
            raise StudyError("Unknown time estimand")
        if duration is None or duration <= 0:
            missing += 1
            continue
        result.append(row if duration == row["duration_seconds"] else {**row, "duration_seconds": duration})
    return result, missing


def support(rows, horizons):
    return {"attempts": len(rows), "events": sum(r["event"] for r in rows),
            "censored": sum(1-r["event"] for r in rows),
            "participants": len({r["participant_key"] for r in rows}),
            "problems": len({(r["problem_contest_id"], r["problem_index"]) for r in rows}),
            "contests": len({r["contest_id"] for r in rows}), "groups": len({r["group"] for r in rows}),
            "at_risk": {str(h): sum(r["duration_seconds"] >= h*60 for r in rows) for h in horizons}}


def summarize_curve(rows, plan, *, apply_filter=True):
    stats = support(rows, plan["horizons_minutes"])
    reason = "empty_cell" if not rows else None
    if not reason and apply_filter and any(stats[k] < v for k, v in plan["display_support"].items()):
        reason = "below_display_support"
    if reason:
        return {"support": stats, "reason": reason, "curve": None, "median_minutes": None,
                "probabilities": {str(h): {"value": None, "reason": reason} for h in plan["horizons_minutes"]}}
    curve = km_fit((r["duration_seconds"], r["event"]) for r in rows)
    probabilities = {}
    for h in plan["horizons_minutes"]:
        p = km_probability(curve, h*60)
        probabilities[str(h)] = {"value": p, "reason": None if p is not None else "insufficient_followup"}
    median = km_quantile(curve, .5)
    return {"support": stats, "reason": None, "curve": curve, "median_minutes": median,
            "median_reason": None if median is not None else "quantile_not_reached", "probabilities": probabilities}


def grid(rows, plan, *, estimand="sequential"):
    validate_plan(plan)
    scoped = rows if plan["scope"] == "all_descriptive" else [r for r in rows if r["partition"] == "train"]
    usable, missing = observations(scoped, estimand)
    cells, deltas, outside = defaultdict(list), defaultdict(list), 0
    for row in usable:
        x = rating_bin(row["old_rating"], plan["old_rating_edges"])
        y = rating_bin(row["problem_rating"], plan["problem_rating_edges"])
        if x is None or y is None:
            outside += 1
            continue
        cells[(x, y)].append(row)
        deltas[delta_bin(row["delta"], plan["delta_bin_width"])].append(row)
    return {"estimand": estimand, "scope": plan["scope"], "missing_time": missing, "outside_grid": outside,
            "cells": [{"x": x, "y": y, **summarize_curve(cells[(x,y)], plan)}
                      for x in plan["old_rating_edges"][:-1] for y in plan["problem_rating_edges"][:-1]],
            "delta": [{"delta_lower": d, **summarize_curve(v, plan)} for d, v in sorted(deltas.items())]}


def select_region(rows, plan, x, y):
    xb, yb = rating_bin(x, plan["old_rating_edges"]), rating_bin(y, plan["problem_rating_edges"])
    if xb is None or yb is None:
        return [], "outside_grid"
    return [r for r in rows if r["quality"] == "valid" and
            (plan["scope"] == "all_descriptive" or r["partition"] == "train") and
            rating_bin(r["old_rating"], plan["old_rating_edges"]) == xb and
            rating_bin(r["problem_rating"], plan["problem_rating_edges"]) == yb], None


def time_bands(curve, h):
    positive(h)
    quantiles = {}
    for name, q in (("T1", .25), ("T2", .5), ("T3", .75)):
        value = km_quantile(curve, q)
        quantiles[name] = {"q": q, "minutes": value,
                           "reason": "quantile_not_reached" if value is None else ("exceeds_budget" if value > h else None)}
    values = [v["minutes"] for v in quantiles.values()]
    tied = any(a is not None and a == b for a, b in zip(values, values[1:]))
    p = km_probability(curve, h*60)
    return {"contract": ANALYSIS_VERSION, "estimator": "Kaplan-Meier with all eligible events and censoring",
            "unit": "minutes", **quantiles, "T4": {"minutes": h, "kind": "operational_budget_not_P100"},
            "p_ac_by_budget": p, "probability_reason": None if p is not None else "insufficient_followup",
            "empty_bands_from_ties": tied, "budget_incompatible": any(v is not None and v > h for v in values),
            "central_region": "P25–P75; variability of attempts, not confidence interval"}


def classify_time(minutes, event, bands):
    positive(minutes)
    if type(event) is not int or event not in (0, 1):
        raise StudyError("Invalid event")
    h = bands["T4"]["minutes"]
    if minutes > h:
        return "ac_after_budget" if event else "censored_after_budget"
    if not event:
        return "censored_at_budget" if minutes == h else "censored_before_budget_unknown_outcome"
    for name, label in (("T1", "very_fast"), ("T2", "faster_than_median"), ("T3", "slower_central_half")):
        value = bands[name]["minutes"]
        if value is None:
            return "partial_landmarks_unavailable"
        if minutes < value:
            return label
    return "slow_within_budget"


def budget_outcomes(rows, h):
    positive(h)
    counts = Counter()
    for r in rows:
        t = r["duration_seconds"] / 60
        key = ("ac_by_h" if t <= h else "ac_after_h") if r["event"] else ("censored_before_h" if t < h else "censored_at_or_after_h")
        counts[key] += 1
    return {k: counts[k] for k in ("ac_by_h", "ac_after_h", "censored_before_h", "censored_at_or_after_h")}


def percentile(values, q):
    ordered = sorted(values)
    position = (len(ordered)-1) * q
    lo, hi = math.floor(position), math.ceil(position)
    return ordered[lo] + (ordered[hi]-ordered[lo]) * (position-lo)


def cluster_bootstrap(rows, plan, h):
    """Whole shared-problem groups, WITH multiplicity; missing replicas stay missing."""
    positive(h)
    clusters = defaultdict(list)
    for r in rows:
        if r.get("group") is None:
            raise StudyError("Missing shared-problem group")
        clusters[r["group"]].append(r)
    keys = sorted(clusters)
    if len(keys) < 2:
        return {"reason": "fewer_than_two_groups", "groups": len(keys), "replicates": [], "statistics": {}}
    rng = random.Random(plan["seed"])
    replicas, draws = [], []
    for _ in range(plan["bootstrap_replicates"]):
        chosen = rng.choices(keys, k=len(keys))
        draws.append(chosen)
        sample = [r for key in chosen for r in clusters[key]]
        curve = km_fit((r["duration_seconds"], r["event"]) for r in sample)
        replicas.append({"p_ac": km_probability(curve, h*60),
                         **{f"p{int(q*100)}_minutes": km_quantile(curve, q) for q in plan["quantiles"]}})
    statistics = {}
    for name in replicas[0]:
        values = [r[name] for r in replicas]
        missing = sum(v is None for v in values)
        statistics[name] = {"missing_replicates": missing, "missing_fraction": missing / len(values),
                            "ci95": None if missing else [percentile(values, .025), percentile(values, .975)],
                            "reason": "unidentified_replicates_no_conditional_CI" if missing else None}
    return {"groups": len(keys), "seed": plan["seed"], "draws_sha256": digest(draws),
            "method": "whole-group percentile 95%; fixed grid; all missing replicates retained",
            "statistics": statistics, "replicates": replicas,
            "limitations": "few groups and repeated participants; no participant independence or predictive validation"}


def stability(rows, plan, x, y, h):
    region, reason = select_region(rows, plan, x, y)
    if reason:
        return {"reason": reason}
    filtered = summarize_curve(region, plan)
    if filtered["reason"]:
        return {"reason": filtered["reason"], "support": filtered["support"]}
    by_group = defaultdict(list)
    for r in region:
        by_group[r["group"]].append(r)
    participant_groups = defaultdict(set)
    for r in region:
        participant_groups[r["participant_key"]].add(r["group"])
    sensitivity = {}
    for estimand in plan["estimands"]:
        changed, missing = observations(region, estimand)
        sensitivity[estimand] = {"missing": missing, **summarize_curve(changed, plan)}
    return {"reason": None, "support": filtered["support"],
            "bootstrap": cluster_bootstrap(region, plan, h),
            "participants_repeated_across_groups": sum(len(v) > 1 for v in participant_groups.values()),
            "by_group": [{"group": key, "contest_ids": sorted({r["contest_id"] for r in v}),
                          **summarize_curve(v, plan, apply_filter=False)} for key, v in sorted(by_group.items())],
            "leave_one_group_out": [{"omitted_group": key,
                                     "omitted_contest_ids": sorted({r["contest_id"] for r in by_group[key]}),
                                     **summarize_curve([r for r in region if r["group"] != key], plan)} for key in sorted(by_group)],
            "estimand_sensitivity": sensitivity}
