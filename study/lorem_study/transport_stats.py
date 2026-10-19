"""Numeric part of the preregistered Ipsum experiment; loaded only with enough data."""

from collections import Counter

import numpy as np

from .baseline import km_fit
from .evaluate import bootstrap_draws, metric_terms, predictions, summarize
from .io import digest
from .transport import counts, freeze


def interval(point, samples, n):
    finite = np.asarray(samples)[np.isfinite(samples)]
    return {"estimate": float(point) if point is not None and np.isfinite(point) else None, "n": int(n),
            "bootstrap_valid": len(finite), "ci95": np.quantile(finite, [.025, .975]).tolist()
            if len(finite) >= .9 * len(samples) else None}


def fit_mapping(train):
    """Equal total weight per participant; only prior ratings, no outcomes."""
    sizes = Counter(r["participant_key"] for r in train)
    weights = np.array([1 / sizes[r["participant_key"]] for r in train])
    x = np.array([r["lorem_rating_before"] for r in train], float)
    y = np.array([r["cf_rating_before"] for r in train], float)
    mx, my = np.average(x, weights=weights), np.average(y, weights=weights)
    variance = np.average((x - mx) ** 2, weights=weights)
    if variance <= 1e-12:
        return {"status": "unavailable_constant_training_rating", "intercept": None, "slope": None}
    slope = float(np.average((x - mx) * (y - my), weights=weights) / variance)
    return {"status": "available" if slope > 0 else "unavailable_nonpositive_slope",
            "intercept": float(my - slope * mx), "slope": slope,
            "training_participants": len(sizes), "training_attempts": len(train)}


def rating_pairs(rows, indices, draws):
    x = np.array([r["cf_rating_before"] for r in rows], float)
    y = np.array([r["lorem_rating_before"] for r in rows], float)
    delta = y - x
    terms = {"lorem_minus_cf_bias": delta, "absolute_difference": np.abs(delta)}
    metrics, _ = summarize(terms, indices, draws)
    # Weighted sums give Pearson correlation for each participant-resampled sample.
    sums = np.stack([np.bincount(indices, weights=v, minlength=draws.shape[1])
                     for v in (np.ones(len(x)), x, y, x*x, y*y, x*y)])
    def correlation(values):
        n, sx, sy, sxx, syy, sxy = values
        denominator = np.sqrt(np.maximum(0, n*sxx-sx*sx) * np.maximum(0, n*syy-sy*sy))
        return np.divide(n*sxy-sx*sy, denominator, out=np.full_like(denominator, np.nan), where=denominator > 1e-10)
    point = correlation(sums.sum(axis=1))
    metrics["pearson"] = interval(point, correlation(sums @ draws.T), len(x))
    return {"counts": counts(rows), "metrics": metrics,
            "difference_sd": float(np.std(delta, ddof=1)) if len(rows) > 1 else None,
            "difference_quantiles_10_50_90": np.quantile(delta, [.1, .5, .9]).tolist(),
            "support": {"cf": [float(x.min()), float(x.max())], "lorem": [float(y.min()), float(y.max())]},
            "note": "prior snapshots only; repeated observations row weighted; bootstrap whole participants; correlation is not equivalence"}


def make_predictions(rows, mapping, baseline, aft):
    all_preds = {}
    for name, field in (("identity", "lorem_rating_before"), ("cf_reference", "cf_rating_before"),
                        ("linear_train_only", "lorem_rating_before")):
        if name == "linear_train_only" and mapping["status"] != "available":
            continue
        features = []
        invalid = []
        for row in rows:
            old = row[field]
            if name == "linear_train_only":
                old = mapping["intercept"] + mapping["slope"] * old
            invalid.append(not np.isfinite(old) or old <= 0)
            # No rounding/clamping: a mapped rating is continuous. Invalid rows remain unavailable.
            features.append({"old_rating": float(old), "problem_rating": row["problem_rating"],
                             "delta": row["problem_rating"] - old})
        generated = predictions(features, baseline, aft)
        for model in ("km_delta", "aft"):
            pred = generated[model]
            pred["p"][invalid] = np.nan
            pred["q"][invalid] = np.nan
            domain = baseline["domain"] if model == "km_delta" else aft["fit"]["domain"]
            in_domain = np.array([not bad and all(domain[k][0] <= f[k] <= domain[k][1] for k in domain)
                                  for f, bad in zip(features, invalid)])
            all_preds[model + "/" + name] = {**pred, "in_domain": in_domain,
                                             "invalid_rating_count": int(sum(invalid))}
        if name == "identity":
            all_preds["d012/lorem"] = {**generated["d012"], "in_domain": np.ones(len(rows), bool),
                                       "invalid_rating_count": 0}
    return all_preds


def calibration(terms, indices, draws, budget):
    """ECE = sum over bins abs(sum IPCW events - sum predictions) / all rows."""
    n_groups = draws.shape[1]
    differences, counts_by_bin = [], []
    for band in range(5):
        key = f"probability/{budget}/bin/{band}"
        values = terms[key + "/observed_ipcw"] - terms[key + "/prediction_all"]
        valid = np.isfinite(values)
        differences.append(np.bincount(indices[valid], weights=values[valid], minlength=n_groups))
        counts_by_bin.append(np.bincount(indices[valid], minlength=n_groups))
    difference, count = np.array(differences), np.array(counts_by_bin).sum(axis=0)
    total = int(count.sum())
    den = draws @ count
    samples = np.divide(np.abs(difference @ draws.T).sum(axis=0), den,
                        out=np.full(len(draws), np.nan), where=den > 0)
    return interval(np.abs(difference.sum(axis=1)).sum() / total if total else None, samples, total)


def score(rows, pred, censoring, indices, draws):
    times = np.array([r["duration_seconds"] / 60 for r in rows])
    events = np.array([r["event"] for r in rows])
    terms, support = metric_terms(times, events, pred, censoring)
    metrics, _ = summarize(terms, indices, draws)
    for budget in (30, 60, 120):
        metrics[f"probability/{budget}/ece_ipcw"] = calibration(terms, indices, draws, budget)
    return {"counts": counts(rows), "metrics": metrics, "support": support,
            "in_domain_fraction": float(pred["in_domain"].mean()),
            "median_coverage_events": int(np.sum(np.isfinite(pred["q"][:, 1]) & (events == 1))) / int(events.sum())
            if events.sum() else 0., "invalid_rating_count": pred["invalid_rating_count"]}


def assess_gate(result, plan):
    """Require upper CI and precision, complete support, domain and denominator coverage."""
    failures = []
    if result["support"]["integration_range_minutes"] != [0, 120]:
        failures.append("incomplete_0_120_support")
    if result["in_domain_fraction"] < plan["minimum_in_domain_fraction"]:
        failures.append("domain_coverage")
    if result["median_coverage_events"] < plan["minimum_median_coverage_events"]:
        failures.append("median_event_coverage")
    limits = [("time/mae", plan["mae_upper_ci_max_minutes"], plan["mae_ci_width_max_minutes"]),
              ("distribution/integrated_brier_ipcw", plan["integrated_brier_upper_ci_max"],
               plan["integrated_brier_ci_width_max"])]
    limits += [(f"probability/{budget}/ece_ipcw", plan["calibration_ece_upper_ci_max"],
                plan["calibration_ece_ci_width_max"]) for budget in plan["budgets_minutes"]]
    for key, upper, width in limits:
        ci = result["metrics"][key]["ci95"]
        if ci is None or ci[1] > upper or ci[1]-ci[0] > width:
            failures.append(key)
    return {"passed": not failures, "failures": failures}


def partition_indices(rows):
    people = sorted({r["participant_key"] for r in rows})
    lookup = {p: i for i, p in enumerate(people)}
    return people, np.array([lookup[r["participant_key"]] for r in rows])


def select_candidate(scores, plan):
    eligible = []
    for name, result in scores.items():
        model, mapping = name.split("/")
        if model not in ("km_delta", "aft") or mapping not in ("identity", "linear_train_only"):
            continue
        value = result["metrics"]["distribution/integrated_brier_ipcw"]["estimate"]
        if (value is not None and result["support"]["integration_range_minutes"] == [0, 120] and
            result["in_domain_fraction"] >= plan["minimum_in_domain_fraction"] and
            result["median_coverage_events"] >= plan["minimum_median_coverage_events"]):
            eligible.append((value, mapping != "identity", model != "km_delta", name))
    return min(eligible)[-1] if eligible else None


def paired_comparisons(rows, preds, chosen, scores, censoring, indices, draws):
    model = chosen.split("/")[0]
    times = np.array([r["duration_seconds"] / 60 for r in rows])
    events = np.array([r["event"] for r in rows])
    result = {}
    for reference in (model + "/identity", model + "/cf_reference"):
        if reference == chosen:
            continue
        ranges = [scores[key]["support"]["integration_range_minutes"] for key in (chosen, reference)]
        end = min(r[1] for r in ranges) if all(ranges) else 0
        a = metric_terms(times, events, preds[chosen], censoring, integration_end=end)[0]
        b = metric_terms(times, events, preds[reference], censoring, integration_end=end)[0]
        differences = {}
        for key in ("time/mae", "distribution/integrated_brier_ipcw"):
            common = np.isfinite(a[key]) & np.isfinite(b[key])
            differences[key] = np.where(common, a[key] - b[key], np.nan)
        metrics, _ = summarize(differences, indices, draws)
        result["selected_minus_" + reference] = {"metrics_common_rows": metrics, "integration_end_minutes": end}
    return result


def evaluate_cohort(parts, baseline, aft, plan, output):
    train, validation = parts["train"], parts["validation"]
    mapping = fit_mapping(train)
    censoring = km_fit([(r["duration_seconds"], r["event"]) for r in train], reverse=True)
    people, indices = partition_indices(validation)
    val_draws = bootstrap_draws(people, plan["seed"], plan["bootstrap_replicates"])
    val_preds = make_predictions(validation, mapping, baseline, aft)
    val_scores = {name: score(validation, pred, censoring, indices, val_draws) for name, pred in val_preds.items()}
    chosen = select_candidate(val_scores, plan)
    selection = {"schema_version": 1, "plan_sha256": digest(plan), "mapping": mapping,
                 "train_sha256": digest(train), "validation_sha256": digest(validation),
                 "censoring_train_only": censoring, "chosen": chosen, "validation": val_scores}
    # This immutable choice is persisted before computing any held-out predictions/metrics.
    freeze(output / "selection.json", selection)
    if chosen is None:
        return {"selection": selection, "test_evaluated": False, "numeric_gate_passed": False,
                "reason": "no_validation_candidate_with_full_followup"}
    test = parts["test"]
    people, indices = partition_indices(test)
    draws = bootstrap_draws(people, plan["seed"], plan["bootstrap_replicates"])
    preds = make_predictions(test, mapping, baseline, aft)
    scores = {name: score(test, pred, censoring, indices, draws) for name, pred in preds.items()}
    strata = {}
    for label, mask in {
        "without_hint": np.array([r["hint_used"] is False for r in test]),
        "with_hint": np.array([r["hint_used"] is True for r in test]),
        "interruptions_unknown": np.array([r.get("interruptions") is None for r in test]),
        "interruptions_recorded": np.array([r.get("interruptions") is not None for r in test]),
    }.items():
        rows = [r for r, yes in zip(test, mask) if yes]
        if not rows:
            strata[label] = {"counts": counts([]), "status": "unavailable; no recorded observations"}
            continue
        subset_pred = {k: v[mask] for k, v in preds[chosen].items() if k in ("p", "q", "in_domain")}
        subset_pred["invalid_rating_count"] = int(np.sum(~np.isfinite(subset_pred["p"][:, 0])))
        strata[label] = score(rows, subset_pred, censoring, indices[mask], draws)
    gates = {"all": assess_gate(scores[chosen], plan)}
    unassisted = strata["without_hint"]
    enough_unassisted = all(unassisted["counts"][k] >= plan["minimum_unassisted_test_" + k]
                            for k in ("participants", "attempts", "events"))
    gates["without_hint"] = (assess_gate(unassisted, plan) if enough_unassisted else
                              {"passed": False, "failures": ["insufficient_unassisted_test"]})
    pairs = {}
    for name, rows in parts.items():
        people, idx = partition_indices(rows)
        ds = bootstrap_draws(people, plan["seed"], plan["bootstrap_replicates"])
        pairs[name] = rating_pairs(rows, idx, ds)
    return {"selection": selection, "test_evaluated": True, "numeric_gate_passed": all(g["passed"] for g in gates.values()),
            "gates": gates, "test": scores, "strata_selected": strata, "rating_pairs": pairs,
            "paired_differences": paired_comparisons(test, preds, chosen, scores, censoring, indices, draws),
            "censoring_assumption": "training Ipsum reverse KM; conditional noninformative censoring unproven",
            "domain_shift": {"source": "sequential contest wall time", "target": "recorded-start training wall time",
                             "test_problem_rating_range": [min(r["problem_rating"] for r in test), max(r["problem_rating"] for r in test)],
                             "test_hint_counts": dict(Counter(str(r["hint_used"]) for r in test)),
                             "interruption_claim": "unknown is unknown; never assume zero pauses"},
            "uncertainty": "participant bootstrap conditional on fixed training fit; shared problems and pseudonym changes may cause residual dependence"}
