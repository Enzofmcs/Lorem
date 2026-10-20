"""Deterministic synthetic policy scenarios, separated from predictive evaluation."""
import math


def round_positive(value):
    return math.floor(value + .5)


def score(base, elapsed, bounds, hint=False, bonus=1.25, reduction=.5):
    t1, t2, t3 = bounds
    factor = bonus if elapsed < t1 else 1 if elapsed < t2 else reduction if elapsed < t3 else 0
    timed = round_positive(base * factor)
    deduction = min(timed, max(2, round_positive(timed * .25))) if hint else 0
    return {"base": base, "elapsed_ms": elapsed, "hint": hint, "multiplier": factor,
            "timed_gain": timed, "hint_deduction": deduction, "gain": timed - deduction}


def simulate():
    bounds = [1_800_000, 3_600_000, 7_200_000]
    parity = [score(base, elapsed, bounds, hint) for base in (0, 8, 24, 40)
              for elapsed in (0, bounds[0]-1, bounds[0], bounds[0]+1, bounds[1]-1, bounds[1], bounds[1]+1,
                              bounds[2]-1, bounds[2], bounds[2]+1) for hint in (False, True)]
    trajectories = []
    # Hypothetical fixed schedules; no inference from observed Codeforces/Ipsum outcomes.
    patterns = {"all_bonus": [0] * 10, "all_normal": [bounds[0]] * 10,
                "all_reduced": [bounds[1]] * 10, "all_zero": [bounds[2]] * 10,
                "mixed": [0, bounds[0], bounds[1], bounds[2], None] * 2}
    for start in (800, 1200, 2000):
        for delta in (-400, 0, 400, 800):
            for hint in (False, True):
                for name, times in patterns.items():
                    rating = legacy_rating = start
                    history = [rating]
                    legacy_history = [legacy_rating]
                    for elapsed in times:
                        problem = max(800, rating + delta)
                        expectation = 1 / (1 + 10 ** ((problem-rating)/400))
                        if elapsed is None:
                            gain = -round_positive(8 + 24*expectation)
                        else:
                            base = round_positive(8 + 32*(1-expectation))
                            gain = score(base, elapsed, bounds, hint)["gain"]
                        rating = max(0, rating+gain)
                        history.append(rating)
                        legacy_problem = max(800, legacy_rating + delta)
                        legacy_expectation = 1 / (1 + 10 ** ((legacy_problem-legacy_rating)/400))
                        if elapsed is None:
                            legacy_gain = -round_positive(8 + 24*legacy_expectation)
                        else:
                            legacy_base = round_positive(8 + 32*(1-legacy_expectation))
                            expected = min(120, max(30, 60+(legacy_problem-legacy_rating)/10))*60000
                            factor = 1 if elapsed <= expected else .5 if elapsed >= expected*2 else 1-.5*(elapsed-expected)/expected
                            legacy_timed = max(1, round_positive(legacy_base*factor))
                            deduction = max(2, round_positive(legacy_timed*.25)) if hint else 0
                            legacy_gain = min(40, max(1, legacy_timed-deduction))
                        legacy_rating = max(0, legacy_rating + legacy_gain)
                        legacy_history.append(legacy_rating)
                    trajectories.append({"initial_rating": start, "problem_delta": delta, "hint": hint,
                                         "schedule": name, "ratings": history, "final_delta": rating-start,
                                         "legacy_ratings": legacy_history, "legacy_final_delta": legacy_rating-start})
    return {"schema_version": 1, "data_kind": "synthetic", "policy_version": "time-curve-v1",
            "bounds_ms": bounds, "bonus_multiplier": 1.25, "reduction_multiplier": .5,
            "activation": "disabled", "parity_cases": parity, "trajectories": trajectories,
            "sensitivity": [{"bonus": b, "reduction": r,
                             "base_24_gains": [score(24, t, bounds, bonus=b, reduction=r)["gain"]
                                               for t in (0, bounds[0], bounds[1], bounds[2])]}
                            for b in (1.0, 1.1, 1.25) for r in (.25, .5, .75)],
            "note": "Hypothetical mechanical scenarios; not predictive accuracy, transport or pedagogical evidence"}
