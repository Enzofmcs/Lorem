"""Internal train/validation scoring; held-out scientific evaluation is History 27."""

import numpy as np

from .io import StudyError

MIN_CENSORING_SURVIVAL = 0.05  # Unsupported, rather than clipping extreme weights.


def validation_score(times, events, probabilities, horizons, censoring):
    times, events, probabilities = np.asarray(times), np.asarray(events), np.asarray(probabilities)
    if (not len(times) or probabilities.shape != (len(times), len(horizons)) or
        not np.all(np.isfinite(times)) or np.any(times <= 0) or
        not np.all(np.isin(events, [0, 1])) or not np.all(np.isfinite(probabilities)) or
        np.any(probabilities < 0) or np.any(probabilities > 1)):
        raise StudyError("Invalid validation times/events/probabilities")
    ct = np.array([r["seconds"] / 60 for r in censoring["table"]])
    cg = np.array([r["survival"] for r in censoring["table"]])
    def g_left(values):
        indices = np.searchsorted(ct, values, side="left") - 1
        return np.where(indices < 0, 1.0, cg[np.maximum(indices, 0)])
    event_weights = g_left(times)
    scores = []
    for j, horizon in enumerate(horizons):
        positive = (events == 1) & (times <= horizon)
        negative = (times > horizon) | ((events == 0) & (times >= horizon))
        known = positive | negative
        gh = float(g_left(np.array([horizon]))[0])
        supported = (horizon <= censoring["max_followup_seconds"] / 60 and
                     gh >= MIN_CENSORING_SURVIVAL and
                     not np.any(event_weights[positive] < MIN_CENSORING_SURVIVAL))
        p = probabilities[:, j]
        brier = None
        if supported:
            # Unknown outcomes contribute zero; denominator remains all validation rows.
            brier = float((np.sum((1 - p[positive]) ** 2 / event_weights[positive]) +
                           np.sum(p[negative] ** 2 / gh)) / len(times))
        ece, bins = None, []
        if np.any(known):
            kp, outcome = p[known], positive[known].astype(float)
            indices = np.minimum((kp * 5).astype(int), 4)
            ece = 0.0
            for band in range(5):
                mask = indices == band
                if np.any(mask):
                    pred, observed = float(kp[mask].mean()), float(outcome[mask].mean())
                    n = int(mask.sum())
                    ece += n / len(kp) * abs(pred - observed)
                    bins.append({"bin": band, "n": n, "prediction": pred, "observed": observed})
        scores.append({"minutes": float(horizon), "brier_ipcw": brier,
                       "known": int(known.sum()), "unknown": int((~known).sum()),
                       "censoring_survival_left": gh, "known_case_ece": ece, "known_case_bins": bins})
    supported = [s for s in scores if s["brier_ipcw"] is not None]
    integrated = None
    if len(supported) >= 2 and supported[-1]["minutes"] > supported[0]["minutes"]:
        integrated = float(np.trapezoid([s["brier_ipcw"] for s in supported],
                                       [s["minutes"] for s in supported]) /
                           (supported[-1]["minutes"] - supported[0]["minutes"]))
    eces = [s["known_case_ece"] for s in scores if s["minutes"] in (30, 60, 120)
            and s["known_case_ece"] is not None and s["brier_ipcw"] is not None]
    return {"n": len(times), "integrated_brier_ipcw": integrated,
            "integration_range_minutes": [supported[0]["minutes"], supported[-1]["minutes"]] if len(supported) >= 2 else None,
            "known_case_ece_mean": float(np.mean(eces)) if eces else None, "horizons": scores,
            "note": "Internal validation only; known-case calibration is subject to selection bias"}
