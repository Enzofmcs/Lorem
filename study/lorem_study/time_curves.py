"""Read-only export of frozen training KM curves. Never fits or opens test labels."""
import math
from .baseline import km_probability, km_quantile
from .io import StudyError, digest

POLICY = "time-curve-v1"
CAP = 7_200_000


def limits(quantiles):
    values = [quantiles.get(str(q)) for q in (.5, .75, .9)]
    if any(v is None for v in values):
        return None, "quantile_not_identified"
    if any(type(v) not in (int, float) or not math.isfinite(v) or v <= 0 for v in values):
        return None, "invalid_quantiles"
    t1, t2, t3 = [math.ceil(v * 60_000) for v in values]
    t3 = min(t3, CAP)
    if not 0 < t1 < t2 < t3 <= CAP:
        return None, "unordered_or_cap_collision"
    return {"t1_ms": t1, "t2_ms": t2, "t3_ms": t3}, None


def milestones(quantiles):
    return {name: (None if quantiles[str(q)] is None else min(CAP, math.ceil(quantiles[str(q)] * 60_000))
                   if name == "t3_ms" else math.ceil(quantiles[str(q)] * 60_000))
            for name, q in (("t1_ms", .5), ("t2_ms", .75), ("t3_ms", .9))}


def envelope(model_version, source_sha, data_kind):
    return {"schema_version": 1, "artifact_version": "time-curves-v1", "policy_version": POLICY,
            "model_version": model_version, "source_sha256": source_sha, "data_kind": data_kind,
            "rating_source": "codeforces_prior", "cap_ms": CAP, "activation": "disabled",
            "activation_reason": "ipsum_transport_and_progression_not_validated", "regions": []}


def export_curves(model):
    if model.get("schema_version") != 1 or model.get("model_version") != "kaplan-meier-v1":
        raise StudyError("Time curves require a frozen kaplan-meier-v1 model")
    width = model["delta_bin_width"]
    if type(width) is not int or width <= 0:
        raise StudyError("Invalid delta bin width")
    artifact = envelope(model["model_version"], digest(model), model["data_kind"])
    artifact["domain"] = model["domain"]
    artifact["grouping"] = "floor((problem_rating-codeforces_prior)/delta_bin_width); no global fallback"
    artifact["delta_bin_width"] = width
    for key, curve in sorted(model["delta_bins"].items(), key=lambda item: int(item[0])):
        quantiles = {}
        reasons = {}
        for q in (.5, .75, .9):
            value = km_quantile(curve, q)
            if value is None or value * 60 > curve["max_followup_seconds"]:
                value = None
            quantiles[str(q)] = value
            reasons[str(q)] = None if value is not None else "insufficient_followup_or_events"
        bounds, reason = limits(quantiles)
        if curve["n"] < model["bin_min_attempts"] or curve["events"] < model["bin_min_events"]:
            bounds, reason = None, "insufficient_sample_support"
        artifact["regions"].append({"id": key, "delta_min": int(key), "delta_max_exclusive": int(key) + width,
            "support": {"attempts": curve["n"], "events": curve["events"],
                        "max_followup_seconds": curve["max_followup_seconds"],
                        "minimum_attempts": model["bin_min_attempts"], "minimum_events": model["bin_min_events"]},
            "probabilities": [{"budget_minutes": b, "p_ac": km_probability(curve, b * 60)} for b in (30, 60, 120)],
            "quantiles_minutes": quantiles, "quantile_reasons": reasons,
            "milestones_ms": milestones(quantiles), "limits": bounds, "unavailable_reason": reason})
    return artifact


def retained_demo(demo):
    """A partial export, explicitly limited to the exact retained pair, not a fitted model."""
    km = demo["km_delta"]
    artifact = envelope(km["model_version"], km["artifact_sha256"], demo["data_kind"])
    artifact["grouping"] = "exact retained query only; full KM training artifact absent from checkout"
    # p75 is unidentifiable in the retained horizon because F(120) < .75.
    if km["probabilities"][-1]["budget_minutes"] != 120 or km["probabilities"][-1]["p_ac"] >= .75:
        raise StudyError("Retained demo changed; review quantile availability")
    artifact["regions"] = [{"id": "retained-1200-1600", "old_rating": demo["old_rating"],
        "problem_rating": demo["problem_rating"], "support": {"attempts": km["training_attempts"],
        "events": None, "max_followup_seconds": 7200, "reason": "event_count_not_retained"},
        "probabilities": km["probabilities"], "quantiles_minutes": {"0.5": km["median_minutes"], "0.75": None, "0.9": None},
        "quantile_reasons": {"0.5": None, "0.75": "not_reached_by_120_minutes", "0.9": "not_reached_by_120_minutes"},
        "milestones_ms": {"t1_ms": math.ceil(km["median_minutes"] * 60000), "t2_ms": None, "t3_ms": None},
        "limits": None, "unavailable_reason": "quantile_not_identified"}]
    return artifact


def lookup(artifact, old_rating, problem_rating, rating_source="codeforces_prior"):
    try:
        return _lookup(artifact, old_rating, problem_rating, rating_source)
    except (KeyError, TypeError, ValueError, OverflowError):
        return {"available": False, "reason": "invalid_artifact", "limits": None}


def _lookup(artifact, old_rating, problem_rating, rating_source):
    def unavailable(reason):
        return {"available": False, "reason": reason, "limits": None}
    if (artifact.get("schema_version") != 1 or artifact.get("policy_version") != POLICY or artifact.get("cap_ms") != CAP
            or artifact.get("artifact_version") != "time-curves-v1" or artifact.get("model_version") != "kaplan-meier-v1"):
        return unavailable("unsupported_artifact")
    if rating_source != "codeforces_prior" or artifact.get("rating_source") != rating_source:
        return unavailable("rating_scale_mismatch")
    if any(type(v) is not int or v <= 0 for v in (old_rating, problem_rating)):
        return unavailable("missing_prior_rating")
    domain = artifact.get("domain")
    if domain and any(not domain[k][0] <= v <= domain[k][1] for k, v in
                      (("old_rating", old_rating), ("problem_rating", problem_rating), ("delta", problem_rating-old_rating))):
        return unavailable("out_of_domain")
    matches = [r for r in artifact["regions"] if
        (r.get("old_rating") == old_rating and r.get("problem_rating") == problem_rating) or
        ("delta_min" in r and r["delta_min"] <= problem_rating-old_rating < r["delta_max_exclusive"])]
    if len(matches) != 1:
        return unavailable("unsupported_region")
    region = matches[0]
    bounds, reason = limits(region["quantiles_minutes"])
    if region["unavailable_reason"] or reason or bounds != region["limits"]:
        return unavailable(region["unavailable_reason"] or reason or "invalid_limits")
    support = region["support"]
    if (support["attempts"] < support["minimum_attempts"] or support["events"] < support["minimum_events"] or
            any(v * 60 > support["max_followup_seconds"] for v in region["quantiles_minutes"].values())):
        return unavailable("invalid_limits_or_support")
    return {"available": True, "limits": bounds, "reason": None, "region": region["id"],
            "model_version": artifact["model_version"], "policy_version": POLICY, "artifact_sha256": digest(artifact)}
