"""Frozen held-out evaluation; paired bootstrap resamples whole contest groups."""

from pathlib import Path

import numpy as np
from scipy.special import ndtri

from .aft import HORIZONS, cdf, locations
from .baseline import expected_d012_minutes, km_fit, km_probability, km_quantile
from .dataset import load_dataset
from .io import StudyError, check_version, digest, read_json, read_rows, write_json
from .scoring import MIN_CENSORING_SURVIVAL

VERSION = "heldout-v1"
REPLICATES = 200
BUDGETS = (30, 60, 120)
LOG_LOSS_EPSILON = 1e-15


def freeze_plan(root):
    """Persist model/split bindings before reading the held-out labels."""
    prepared = read_json(root / "prepared" / "manifest.json")
    splits = read_json(root / "splits.json")
    baseline = read_json(root / "baseline" / "model.json")
    aft = read_json(root / "aft" / "model.json")
    for value in (prepared, splits, baseline, aft):
        check_version(value)
    if baseline.get("model_version") != "kaplan-meier-v1" or aft.get("model_version") != "aft-v1":
        raise StudyError("Unsupported evaluation model version")
    if aft["selection"]["status"] != "selected":
        raise StudyError("AFT selection gate failed; held-out evaluation is unavailable")
    if (aft["training_contest_ids"] != splits["partitions"]["train"] or
        baseline["training_contest_ids"] != splits["partitions"]["train"] or
        aft["validation_contest_ids"] != splits["partitions"]["validation"]):
        raise StudyError("Model partitions differ from frozen split")
    plan = {"schema_version": 1, "evaluation_version": VERSION, "seed": splits["seed"],
            "bootstrap_replicates": REPLICATES, "bootstrap_unit": "whole shared-contest group; paired across models",
            "prepared_sha256": digest(prepared), "splits_sha256": digest(splits),
            "baseline_sha256": digest(baseline), "aft_sha256": digest(aft),
            "selection": aft["selection"]["chosen"], "budgets_minutes": list(BUDGETS),
            "integration_grid_minutes": HORIZONS, "min_censoring_survival": MIN_CENSORING_SURVIVAL,
            "rating_slice_width": 400, "probability_bins": 5,
            "sensitivity": "last-submission observation window; fixed predictions, censoring refit on train only",
            "log_loss_epsilon": LOG_LOSS_EPSILON,
            "note": "Frozen before opening test labels; reruns reproduce, never retune"}
    path = root / "evaluation" / "plan.json"
    if path.exists() and read_json(path) != plan:
        raise StudyError("Evaluation already frozen for different inputs; do not reopen test for tuning")
    write_json(path, plan)
    return plan, baseline, aft


def bootstrap_draws(groups, seed, replicates=REPLICATES):
    if len(groups) < 2:
        raise StudyError("Evaluation requires at least two independent test groups")
    rng = np.random.default_rng(seed)
    draws = rng.integers(0, len(groups), (replicates, len(groups)))
    return np.array([np.bincount(row, minlength=len(groups)) for row in draws])


def predictions(features, baseline, aft):
    n = len(features)
    result = {}
    for name in ("km_global", "km_delta"):
        p = np.full((n, len(HORIZONS)), np.nan)
        quantiles = np.full((n, 3), np.nan)
        cache = {}
        for i, f in enumerate(features):
            bucket = str((f["delta"] // baseline["delta_bin_width"]) * baseline["delta_bin_width"])
            key = bucket if name == "km_delta" and bucket in baseline["delta_bins"] else "global"
            if key not in cache:
                curve = baseline["global"] if key == "global" else baseline["delta_bins"][key]
                cache[key] = ([km_probability(curve, h * 60) for h in HORIZONS],
                              [km_quantile(curve, q) for q in (.1, .5, .9)])
            p[i], quantiles[i] = cache[key]
        result[name] = {"p": p, "q": quantiles}
    model = aft["fit"]
    p = cdf(model, features, HORIZONS)
    p[:, np.asarray(HORIZONS) > model["max_followup_minutes"]] = np.nan
    shifts = (ndtri([.1, .5, .9]) if model["family"] == "lognormal" else
              np.log(-np.log1p(-np.array([.1, .5, .9]))))
    with np.errstate(over="ignore", under="ignore"):
        q = np.exp(locations(model, features)[:, None] + model["scale"] * shifts)
    q[(q > model["max_followup_minutes"]) | (q <= 0) | ~np.isfinite(q)] = np.nan
    result["aft"] = {"p": p, "q": q}
    result["d012"] = {"p": np.full_like(p, np.nan), "q": np.column_stack([
        np.full(n, np.nan), [expected_d012_minutes(f["old_rating"], f["problem_rating"]) for f in features],
        np.full(n, np.nan)])}
    return result


def metric_terms(times, events, pred, censoring):
    """Per-row contributions; NaN means unavailable, unknown IPCW contributes zero."""
    times, events = np.asarray(times, float), np.asarray(events)
    if (not len(times) or not np.all(np.isfinite(times)) or np.any(times <= 0) or
        not np.all(np.isin(events, [0, 1]))):
        raise StudyError("Evaluation requires positive times and binary events")
    p, q = pred["p"], pred["q"]
    if p.shape != (len(times), len(HORIZONS)) or q.shape != (len(times), 3):
        raise StudyError("Prediction dimensions differ from evaluation rows")
    if np.any(np.isfinite(p) & ((p < 0) | (p > 1))):
        raise StudyError("Invalid evaluation probability")
    ct = np.array([r["seconds"] / 60 for r in censoring["table"]])
    cg = np.array([r["survival"] for r in censoring["table"]])
    def g_left(values):
        indices = np.searchsorted(ct, values, side="left") - 1
        return np.where(indices < 0, 1., cg[np.maximum(indices, 0)])
    ge = g_left(times)
    terms, support = {}, []
    error = np.where(events == 1, q[:, 1] - times, np.nan)
    terms.update({"time/mae": np.abs(error), "time/median_absolute_error": np.abs(error),
                  "time/rmse": error ** 2, "time/bias": error})
    interval = (events == 1) & np.all(np.isfinite(q[:, [0, 2]]), axis=1)
    terms["interval80/coverage"] = np.where(interval, ((times >= q[:, 0]) & (times <= q[:, 2])).astype(float), np.nan)
    terms["interval80/width_minutes"] = np.where(interval, q[:, 2] - q[:, 0], np.nan)
    contributions = []
    supported_horizons = []
    for j, horizon in enumerate(HORIZONS):
        prob = p[:, j]
        positive = (events == 1) & (times <= horizon)
        negative = times >= horizon
        negative &= ~positive
        known = positive | negative
        gh = float(g_left([horizon])[0])
        min_g = min(gh, float(ge[positive].min()) if np.any(positive) else 1.)
        available = (horizon <= censoring["max_followup_seconds"] / 60 and min_g >= MIN_CENSORING_SURVIVAL)
        brier = np.full(len(times), np.nan)
        if available:
            brier[:] = 0
            brier[positive] = (1 - prob[positive]) ** 2 / ge[positive]
            brier[negative] = prob[negative] ** 2 / gh
            brier[~np.isfinite(prob)] = np.nan
        # Integration uses a common supported prefix, never bridges unavailable gaps.
        if available and np.all(np.isfinite(prob)) and len(supported_horizons) == j:
            contributions.append(brier)
            supported_horizons.append(horizon)
        if horizon not in BUDGETS:
            continue
        key = f"probability/{horizon}"
        terms[key + "/brier_ipcw"] = brier
        terms[key + "/brier_known"] = np.where(known, (prob - positive) ** 2, np.nan)
        safe = np.clip(prob, LOG_LOSS_EPSILON, 1 - LOG_LOSS_EPSILON)
        loss = np.where(positive, -np.log(safe), -np.log1p(-safe))
        terms[key + "/log_loss_known"] = np.where(known, loss, np.nan)
        bins = np.minimum(np.floor(np.nan_to_num(prob) * 5).astype(int), 4)
        for band in range(5):
            mask = (bins == band) & np.isfinite(prob)
            bkey = key + f"/bin/{band}"
            terms[bkey + "/prediction_known"] = np.where(mask & known, prob, np.nan)
            terms[bkey + "/observed_known"] = np.where(mask & known, positive.astype(float), np.nan)
            # Bin-level IPCW event fraction: denominator includes unknown outcomes.
            outcome = np.full(len(times), np.nan)
            if available:
                outcome[:] = 0
                outcome[positive] = 1 / ge[positive]
            terms[bkey + "/observed_ipcw"] = np.where(mask, outcome, np.nan)
            terms[bkey + "/prediction_all"] = np.where(mask, prob, np.nan)
        support.append({"minutes": horizon, "known": int(known.sum()), "unknown": int((~known).sum()),
                        "prediction_available": int(np.isfinite(prob).sum()), "g_left": gh,
                        "minimum_relevant_g": min_g, "max_ipcw_weight": 1 / min_g if available else None,
                        "ipcw_available": available and bool(np.any(np.isfinite(prob))),
                        "reason": ("model_has_no_probability" if not np.any(np.isfinite(prob)) else
                                   (None if available else "followup_or_censoring_support"))})
    terms["distribution/integrated_brier_ipcw"] = (
        np.trapezoid(np.array(contributions), supported_horizons, axis=0) /
        (supported_horizons[-1] - supported_horizons[0]) if len(supported_horizons) > 1 else
        np.full(len(times), np.nan))
    return terms, {"horizons": support, "integration_range_minutes":
                   [supported_horizons[0], supported_horizons[-1]] if len(supported_horizons) > 1 else None}


def summarize(terms, group_indices, draws):
    """Group sums preserve row denominators while whole groups are resampled."""
    result, samples = {}, {}
    n_groups = draws.shape[1]
    for key, values in terms.items():
        values = np.asarray(values, float)
        valid = np.isfinite(values)
        sums = np.bincount(group_indices[valid], weights=values[valid], minlength=n_groups)
        counts = np.bincount(group_indices[valid], minlength=n_groups)
        den = draws @ counts
        boot = np.divide(draws @ sums, den, out=np.full(len(draws), np.nan), where=den > 0)
        point = float(values[valid].mean()) if np.any(valid) else None
        if key == "time/median_absolute_error" and np.any(valid):
            order = np.argsort(values[valid], kind="stable")
            sorted_values, sorted_groups = values[valid][order], group_indices[valid][order]
            point = float(np.median(sorted_values))
            for i, draw in enumerate(draws):
                weights = draw[sorted_groups]
                if not weights.sum():
                    continue
                cumulative = np.cumsum(weights)
                # Standard median of the expanded sample, including even-size averaging.
                lo = np.searchsorted(cumulative, (weights.sum() - 1) // 2 + 1)
                hi = np.searchsorted(cumulative, weights.sum() // 2 + 1)
                boot[i] = (sorted_values[lo] + sorted_values[hi]) / 2
        if key == "time/rmse":
            point = float(np.sqrt(point)) if point is not None else None
            boot = np.sqrt(boot)
        finite = boot[np.isfinite(boot)]
        result[key] = {"estimate": point, "n": int(valid.sum()), "bootstrap_valid": len(finite),
                       "ci95": np.quantile(finite, [.025, .975]).tolist() if len(finite) >= .9 * len(draws) else None}
        samples[key] = boot
    return result, samples


def evaluate(root):
    root = Path(root)
    plan, baseline, aft = freeze_plan(root)
    prepared, splits, _, parts = load_dataset(root)
    train, test = parts["train"], parts["test"]
    if not test:
        raise StudyError("No valid held-out attempts")
    if (digest(train) != baseline["training_sha256"] or digest(train) != aft["fit"]["training_sha256"] or
        digest(parts["validation"]) != aft["validation_sha256"]):
        raise StudyError("Frozen model does not match training/validation rows")
    censoring = km_fit([(r["duration_seconds"], r["event"]) for _, r in train], reverse=True)
    if censoring != baseline["censoring_train_only"] or censoring != aft["censoring_train_only"]:
        raise StudyError("Model censoring curve does not match training only")
    groups = [g for g in splits["groups"] if set(g) <= set(splits["partitions"]["test"])]
    group_for = {cid: i for i, group in enumerate(groups) for cid in group}
    indices = np.array([group_for[f["contest_id"]] for f, _ in test])
    draws = bootstrap_draws(groups, plan["seed"])
    features = [f for f, _ in test]
    times = np.array([r["duration_seconds"] / 60 for _, r in test])
    events = np.array([r["event"] for _, r in test])
    preds = predictions(features, baseline, aft)
    models, terms_by_model = {}, {}
    for name, pred in preds.items():
        terms, support = metric_terms(times, events, pred, censoring)
        metrics, _ = summarize(terms, indices, draws)
        models[name] = {"metrics": metrics, "support": support}
        terms_by_model[name] = terms
    differences = {}
    for name in ("km_global", "km_delta", "d012"):
        left, right = {}, {}
        for key, a in terms_by_model["aft"].items():
            b = terms_by_model[name][key]
            mask = np.isfinite(a) & np.isfinite(b)
            left[key], right[key] = np.where(mask, a, np.nan), np.where(mask, b, np.nan)
        lm, lb = summarize(left, indices, draws)
        rm, rb = summarize(right, indices, draws)
        difference = {}
        for key in lm:
            if "/bin/" in key:
                continue
            sample = lb[key] - rb[key]
            finite = sample[np.isfinite(sample)]
            difference[key] = {"estimate": lm[key]["estimate"] - rm[key]["estimate"] if lm[key]["estimate"] is not None else None,
                               "n_common": lm[key]["n"], "bootstrap_valid": len(finite),
                               "ci95": np.quantile(finite, [.025, .975]).tolist() if len(finite) >= .9 * len(draws) else None}
        differences["aft_minus_" + name] = difference
    # Fixed 400-point rating slices; excluded rows never receive invented errors.
    slices = {}
    for field in ("old_rating", "problem_rating", "delta"):
        bands = np.array([(f[field] // 400) * 400 for f in features])
        slices[field] = {}
        for band in sorted(set(bands.tolist())):
            mask = bands == band
            selected = {}
            for name, terms in terms_by_model.items():
                selected[name] = summarize({k: np.where(mask, v, np.nan) for k, v in terms.items()
                                           if k.startswith("time/") or k == "distribution/integrated_brier_ipcw"}, indices, draws)[0]
            slices[field][str(band)] = {"n": int(mask.sum()), "events": int(events[mask].sum()),
                                       "censored": int((1 - events[mask]).sum()), "models": selected}
    # Alternative observation windows; predictions remain frozen, no fitting/tuning on test.
    sensitivity = {}
    alternate_train = [(r["duration_seconds"] if r["event"] else r.get("sensitivity_last_submission_seconds"), r["event"])
                       for _, r in train]
    alternate_train = [(t, e) for t, e in alternate_train if t is not None and t > 0]
    alt_times = np.array([(r["duration_seconds"] if r["event"] else r.get("sensitivity_last_submission_seconds")) or np.nan for _, r in test]) / 60
    alt_mask = np.isfinite(alt_times) & (alt_times > 0)
    if alternate_train and np.any(alt_mask):
        alt_censoring = km_fit(alternate_train, reverse=True)
        for name, pred in preds.items():
            terms, support = metric_terms(alt_times[alt_mask], events[alt_mask],
                                         {k: v[alt_mask] for k, v in pred.items()}, alt_censoring)
            sensitivity[name] = {"metrics": summarize(terms, indices[alt_mask], draws)[0], "support": support}
    labels = list(read_rows(root / "prepared" / "labels.jsonl"))
    quality = {}
    for row in labels:
        if row["contest_id"] in splits["partitions"]["test"]:
            key = "valid" if row["quality"] == "valid" else "excluded:" + str(row["reason"])
            quality[key] = quality.get(key, 0) + 1
    result = {"schema_version": 1, "evaluation_version": VERSION, "data_kind": prepared["data_kind"],
              "plan_sha256": digest(plan), "plan": plan, "test_contest_ids": splits["partitions"]["test"],
              "test_groups": groups, "n": len(test), "events": int(events.sum()), "censored": int((1 - events).sum()),
              "bootstrap": {"replicates": REPLICATES, "seed": plan["seed"], "draws_sha256": digest(draws.tolist()),
                            "method": "paired percentile 95%; fixed fitted models and train censoring; attempted-row weighted",
                            "warning": "Few test groups; intervals omit fitting uncertainty and may be unstable"},
              "models": models, "paired_differences_common_support": differences, "rating_slices": slices,
              "quality_funnel_test": quality, "population_funnel": {k: prepared.get(k) for k in
                  ("observed_attempts", "valid_events", "valid_censored", "excluded", "coverage", "reasons")},
              "extrapolated_aft_rows": sum(any(not aft["fit"]["domain"][k][0] <= f[k] <= aft["fit"]["domain"][k][1]
                                              for k in ("old_rating", "problem_rating", "delta")) for f in features),
              "sensitivity_last_submission": {"train_n": len(alternate_train), "test_n": int(alt_mask.sum()),
                                               "test_missing": int((~alt_mask).sum()), "models": sensitivity},
              "limitations": ["Sequential wall-clock approximation, not effective dedication",
                              "Event-only time and interval scores favor solved attempts; each denominator is explicit",
                              "Known-case scores/calibration are selected; censored-before-budget outcomes remain unknown",
                              "IPCW assumes independent censoring with a marginal train-only censoring curve",
                              "Bin IPCW estimates can exceed one; no clipping of weights or observed fractions",
                              "Intervals are predictive 10/90 quantiles; CI95 is uncertainty of evaluation metrics",
                              "Participants repeat across contests; group bootstrap does not remove participant dependence",
                              "Problem ratings are from collection; missing ratings and selected prefixes limit coverage",
                              "No temporal correction, pedagogical claim, or transport validation for Ipsums"]}
    write_json(root / "evaluation" / "metrics.json", result)
    write_json(root / "evaluation" / "manifest.json", {"schema_version": 1, "plan_sha256": digest(plan),
               "metrics_sha256": digest(result), "status": "evaluated frozen test; no retuning"})
    return result
