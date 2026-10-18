"""Two right-censored AFT likelihoods, fit with pinned SciPy; no test-set tuning."""

from importlib.metadata import version
import math
from pathlib import Path

import numpy as np
from scipy.optimize import minimize
from scipy.special import log_ndtr, ndtr, ndtri

from .baseline import km_fit, km_probability
from .dataset import load_dataset
from .io import StudyError, digest, write_json, write_rows
from .scoring import validation_score

MODEL_VERSION = "aft-v1"
GRID = [(family, penalty) for family in ("lognormal", "weibull") for penalty in (0.0, 0.01, 0.1)]
HORIZONS = list(range(0, 121, 5))
CALIBRATION_TOLERANCE = 0.05


def objective(theta, x, log_t, event, family, penalty):
    beta, log_scale = theta[:3], theta[3]
    scale = np.exp(log_scale)
    z = (log_t - x @ beta) / scale
    if family == "lognormal":
        log_sf = log_ndtr(-z)
        mills = np.exp(-0.5 * z * z - 0.5 * np.log(2 * np.pi) - log_sf)
        losses = np.where(event == 1, log_t + log_scale + 0.5 * z * z + 0.5 * np.log(2 * np.pi), -log_sf)
        dz = np.where(event == 1, z, mills)
        ds = np.where(event == 1, 1 - z * z, -mills * z)
    elif family == "weibull":
        # A numerical guard during line-search only; accepted fits must never reach it.
        ez = np.exp(np.minimum(z, 500))
        losses = ez - event * z + event * (log_scale + log_t)
        dz = ez - event
        ds = event - dz * z
    else:
        raise StudyError("Unsupported AFT distribution")
    loss = losses.mean() + penalty * np.dot(beta[1:], beta[1:]) / 2
    grad = np.r_[-x.T @ dz / (len(x) * scale), ds.mean()]
    grad[1:3] += penalty * beta[1:]
    return float(loss), grad


def fit(rows, family, penalty, *, maxiter=500):
    if len(rows) < 20:
        raise StudyError("AFT needs at least 20 valid training attempts")
    raw = np.array([[f["old_rating"] / 100, f["delta"] / 100] for f, _ in rows], dtype=float)
    times = np.array([r["duration_seconds"] / 60 for _, r in rows], dtype=float)
    event = np.array([r["event"] for _, r in rows])
    if (not np.all(np.isfinite(raw)) or not np.all(np.isfinite(times)) or np.any(times <= 0) or
        not np.all(np.isin(event, [0, 1])) or event.sum() < 5):
        raise StudyError("AFT needs finite positive times and at least five observed events")
    center = raw.mean(axis=0)
    x = np.column_stack([np.ones(len(raw)), raw - center])
    if np.linalg.matrix_rank(x) != 3:
        raise StudyError("AFT training ratings are not identifiable (rank deficient)")
    log_t = np.log(times)
    start = np.array([np.median(log_t[event == 1]), 0.0, 0.0, 0.0])
    # Scale optimizer coordinates using training only, then export slopes per 100 rating.
    # This prevents a large first slope step stalling Weibull's exponential likelihood.
    coordinate_scale = np.r_[1.0, raw.std(axis=0), 1.0]
    def scaled_objective(phi):
        loss, gradient = objective(phi / coordinate_scale, x, log_t, event, family, penalty)
        return loss, gradient / coordinate_scale
    bounds = [(-20, 20), (-5, 5), (-5, 5), (-3, 3)]
    result = minimize(scaled_objective, start * coordinate_scale, jac=True,
                      method="L-BFGS-B", bounds=[(lo * s, hi * s) for (lo, hi), s in zip(bounds, coordinate_scale)],
                      options={"maxiter": maxiter, "ftol": 1e-11, "gtol": 1e-6, "maxls": 50})
    theta = result.x / coordinate_scale
    loss, gradient = objective(theta, x, log_t, event, family, penalty)
    gradient_max = float(np.max(np.abs(gradient)))
    z = (log_t - x @ theta[:3]) / np.exp(theta[3])
    on_bound = any(abs(value - lo) < 1e-5 or abs(value - hi) < 1e-5
                   for value, (lo, hi) in zip(theta, bounds))
    if (not result.success or not np.all(np.isfinite(result.x)) or not math.isfinite(result.fun) or
        not math.isfinite(gradient_max) or gradient_max > 1e-3 or on_bound or
        (family == "weibull" and np.any(z >= 500))):
        raise StudyError(f"AFT convergence/identifiability failure ({family}, penalty={penalty}): {result.message}")
    return {"family": family, "penalty": penalty, "coefficients": theta[:3].tolist(),
            "scale": float(np.exp(theta[3])), "center": center.tolist(),
            "optimizer_rating_scale_train_only": coordinate_scale[1:3].tolist(),
            "feature_spec": ["intercept", "oldRating/100 centered on training", "delta/100 centered on training"],
            "time_unit": "minutes", "training_n": len(rows), "training_events": int(event.sum()),
            "training_censored": int(len(rows) - event.sum()), "training_sha256": digest(rows),
            "max_followup_minutes": float(times.max()),
            "domain": {k: [min(f[k] for f, _ in rows), max(f[k] for f, _ in rows)]
                       for k in ("old_rating", "problem_rating", "delta")},
            "time_factors_per_100_rating": {"old_rating_at_fixed_delta": math.exp(theta[1]),
                                            "delta_at_fixed_old_rating": math.exp(theta[2])},
            "convergence": {"success": True, "iterations": int(result.nit), "objective": loss,
                            "gradient_max_abs": gradient_max, "message": str(result.message)}}


def locations(model, features):
    raw = np.array([[f["old_rating"] / 100, f["delta"] / 100] for f in features], dtype=float)
    return model["coefficients"][0] + (raw - model["center"]) @ np.array(model["coefficients"][1:])


def cdf(model, features, horizons):
    if (model.get("family") not in ("lognormal", "weibull") or
        type(model.get("scale")) not in (int, float) or not math.isfinite(model["scale"]) or model["scale"] <= 0 or
        not np.all(np.isfinite(model.get("coefficients", []))) or
        len(model.get("coefficients", [])) != 3):
        raise StudyError("Invalid AFT parameters")
    mu = locations(model, features)
    h = np.asarray(horizons, dtype=float)
    if np.any(h < 0) or not np.all(np.isfinite(h)):
        raise StudyError("Invalid CDF horizons")
    with np.errstate(divide="ignore", over="ignore", under="ignore"):
        z = (np.log(h)[None, :] - mu[:, None]) / model["scale"]
        values = ndtr(z) if model["family"] == "lognormal" else -np.expm1(-np.exp(z))
    if not np.all(np.isfinite(values)) or np.any(values < 0) or np.any(values > 1):
        raise StudyError("Non-finite AFT probabilities")
    return values


def grouped_folds(splits):
    train = set(splits["partitions"]["train"])
    groups = [g for g in splits["groups"] if set(g) <= train]
    if len(groups) < 3:
        return []
    folds = [[], [], []]
    for group in sorted(groups, key=lambda g: (-len(g), digest([splits["seed"], g]))):
        folds[min(range(3), key=lambda i: (len(folds[i]), i))].extend(group)
    return [sorted(f) for f in folds]


def score(model, training, validation):
    observations = [(r["duration_seconds"], r["event"]) for _, r in training]
    censoring = km_fit(observations, reverse=True)
    features = [f for f, _ in validation]
    times = np.array([r["duration_seconds"] / 60 for _, r in validation])
    events = np.array([r["event"] for _, r in validation])
    values = cdf(model, features, HORIZONS)
    scores = validation_score(times, events, values, HORIZONS, censoring)
    medians = np.exp(locations(model, features) + model["scale"] *
                     (float(ndtri(.5)) if model["family"] == "lognormal" else math.log(-math.log(.5))))
    scores["event_mae_minutes"] = float(np.mean(np.abs(medians[events == 1] - times[events == 1]))) if np.any(events == 1) else None
    return scores


def train_aft(root):
    root = Path(root)
    prepared, splits, features, parts = load_dataset(root)
    training, validation = parts["train"], parts["validation"]
    if not validation:
        raise StudyError("No valid validation observations")
    folds = grouped_folds(splits)
    fold_support = bool(folds) and all(
        len([r for f, r in training if f["contest_id"] not in fold]) >= 20 and
        sum(r["event"] for f, r in training if f["contest_id"] not in fold) >= 5 and
        any(f["contest_id"] in fold for f, _ in training) for fold in folds)
    global_km = km_fit([(r["duration_seconds"], r["event"]) for _, r in training])
    censoring = km_fit([(r["duration_seconds"], r["event"]) for _, r in training], reverse=True)
    km_values = [km_probability(global_km, h * 60) for h in HORIZONS]
    # Beyond training follow-up the scorer marks horizons unsupported; finite placeholders never enter scores.
    reference = validation_score([r["duration_seconds"] / 60 for _, r in validation],
                                 [r["event"] for _, r in validation],
                                 np.tile([v if v is not None else 0 for v in km_values], (len(validation), 1)),
                                 HORIZONS, censoring)
    candidates, models = [], {}
    for family, penalty in GRID:
        key = f"{family}:{penalty:g}"
        record = {"candidate": key, "family": family, "penalty": penalty, "status": "failed"}
        try:
            model = fit(training, family, penalty)
            record.update(status="converged", convergence=model["convergence"], validation=score(model, training, validation))
            cv = []
            if fold_support:
                for fold in folds:
                    tr = [(f, r) for f, r in training if f["contest_id"] not in fold]
                    va = [(f, r) for f, r in training if f["contest_id"] in fold]
                    try:
                        cv_model = fit(tr, family, penalty)
                        result = score(cv_model, tr, va)
                        cv.append({"heldout_contest_ids": fold, "status": "converged",
                                   "integrated_brier_ipcw": result["integrated_brier_ipcw"]})
                    except StudyError as error:
                        cv.append({"heldout_contest_ids": fold, "status": "unavailable", "reason": str(error)})
            record["grouped_training_cv"] = cv
            cv_scores = [r["integrated_brier_ipcw"] for r in cv if r["status"] == "converged" and r["integrated_brier_ipcw"] is not None]
            record["grouped_cv_mean_brier"] = float(np.mean(cv_scores)) if cv_scores else None
            val_ece, ref_ece = record["validation"]["known_case_ece_mean"], reference["known_case_ece_mean"]
            record["calibration_eligible"] = (val_ece is not None and ref_ece is not None and
                                               val_ece <= ref_ece + CALIBRATION_TOLERANCE)
            models[key] = model
        except StudyError as error:
            record["reason"] = str(error)
        candidates.append(record)
    available = [c for c in candidates if c["status"] == "converged" and c["validation"]["integrated_brier_ipcw"] is not None]
    if not available:
        write_json(root / "aft" / "failure.json", {"status": "unavailable", "candidates": candidates})
        raise StudyError("No converged AFT candidate with supported validation horizons")
    eligible = [c for c in available if c["calibration_eligible"]]
    ordered = sorted(eligible or available, key=lambda c: (c["validation"]["integrated_brier_ipcw"],
                      c["grouped_cv_mean_brier"] if c["grouped_cv_mean_brier"] is not None else math.inf,
                      c["validation"]["event_mae_minutes"] if c["validation"]["event_mae_minutes"] is not None else math.inf,
                      c["family"] != "lognormal", -c["penalty"]))
    chosen = ordered[0]
    artifact = {"schema_version": 1, "model_version": MODEL_VERSION, "data_kind": prepared["data_kind"],
                "protocol_version": prepared["config"]["protocol_version"],
                "eligibility_version": prepared["config"]["eligibility_version"],
                "training_contest_ids": splits["partitions"]["train"],
                "validation_contest_ids": splits["partitions"]["validation"],
                "validation_sha256": digest(validation), "seed": splits["seed"],
                "selection": {"chosen": chosen["candidate"], "status": "selected" if eligible else "calibration_gate_failed; diagnostic_only",
                              "method": "minimum validation integrated IPCW Brier; calibration tolerance to training global KM 0.05; grouped CV/MAE tie-break",
                              "grid": [[f, p] for f, p in GRID], "horizons_minutes": HORIZONS,
                              "calibration_tolerance": CALIBRATION_TOLERANCE, "min_censoring_survival": 0.05,
                              "grouped_cv_folds": folds if fold_support else [], "reference_validation": reference,
                              "candidates": candidates},
                "fit": models[chosen["candidate"]],
                "censoring_train_only": censoring,
                "dependencies": {"numpy": version("numpy"), "scipy": version("scipy")},
                "limitations": ["Right censoring assumed conditionally independent; abandonment may violate this",
                                "Sequential wall-clock approximation and selected prefixes",
                                "Parametric extrapolation is flagged; unsupported follow-up is unavailable",
                                "Validation scores select the model; no held-out scientific evaluation (History 27)"]}
    output = root / "aft"
    artifact_sha = digest(artifact)
    archived = output / "artifacts" / f"{artifact_sha}.json"
    if not archived.exists():
        write_json(archived, artifact)
    write_json(output / "model.json", artifact)
    part_for = {cid: part for part, ids in splits["partitions"].items() for cid in ids}
    predictions = []
    for f in features:
        if f["contest_id"] in part_for and type(f["old_rating"]) is int and f["old_rating"] > 0 and type(f["problem_rating"]) is int:
            predictions.append({"sample_id": f["sample_id"], "partition": part_for[f["contest_id"]],
                                **predict_aft(artifact, f["old_rating"], f["problem_rating"], prepared["config"]["budgets_minutes"], _sha=artifact_sha)})
    write_rows(output / "predictions.jsonl", predictions)
    write_json(output / "manifest.json", {"schema_version": 1, "data_kind": prepared["data_kind"],
               "artifact_sha256": artifact_sha, "prepared_sha256": digest(prepared), "splits_sha256": digest(splits),
               "predictions_sha256": digest(predictions), "status": "model selected on validation; test evaluation not executed"})
    return artifact


def predict_aft(artifact, old_rating, problem_rating, budgets, *, _sha=None):
    if artifact.get("model_version") != MODEL_VERSION or artifact.get("schema_version") != 1:
        raise StudyError("Unsupported AFT model version")
    if any(type(v) is not int or v <= 0 for v in (old_rating, problem_rating)):
        raise StudyError("Ratings must be positive integers with prior history")
    if not budgets or any(type(b) not in (int, float) or not math.isfinite(b) or b <= 0 for b in budgets):
        raise StudyError("Budgets must be finite and positive")
    model = artifact["fit"]
    f = {"old_rating": old_rating, "problem_rating": problem_rating, "delta": problem_rating - old_rating}
    outside = [k for k, value in f.items() if not model["domain"][k][0] <= value <= model["domain"][k][1]]
    mu = float(locations(model, [f])[0])
    probabilities = cdf(model, [f], budgets)[0]
    diagnostic_only = artifact["selection"]["status"] != "selected"
    output = []
    for horizon, probability in zip(budgets, probabilities):
        support = "available"
        if diagnostic_only:
            support = "calibration_gate_failed"
        elif horizon > model["max_followup_minutes"]:
            support = "insufficient_followup"
        elif outside:
            support = "extrapolation"
        output.append({"budget_minutes": horizon, "p_ac": float(probability) if support in ("available", "extrapolation") else None, "support": support})
    quantiles, supports = [], []
    for q in (.1, .5, .9):
        shift = float(ndtri(q)) if model["family"] == "lognormal" else math.log(-math.log1p(-q))
        log_t = mu + model["scale"] * shift
        value = math.exp(log_t) if -700 < log_t < 700 else None
        supported = value is not None and value <= model["max_followup_minutes"] and not diagnostic_only
        quantiles.append(value if supported else None)
        supports.append("available" if supported and not outside else ("extrapolation" if supported else "insufficient_followup_or_model_unavailable"))
    return {"data_kind": artifact["data_kind"], "model_version": MODEL_VERSION,
            "artifact_sha256": _sha or digest(artifact), "family": model["family"], "out_of_domain": outside,
            "probabilities": output, "median_minutes": quantiles[1],
            "predictive_interval_80_minutes": [quantiles[0], quantiles[2]], "quantile_support_10_50_90": supports,
            "interval_kind": "parametric predictive quantiles, not confidence intervals"}
