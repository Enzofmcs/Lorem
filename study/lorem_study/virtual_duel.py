"""Experimental virtual duel. Probabilities and ratings use millionths.

The KM reference describes selected CF attempts, not a validated Lorem scale.
No floor/clamp is applied to the experimental trajectory.
"""
from bisect import bisect_left, bisect_right

SCALE = 1_000_000
POLICY = 'virtual-duel-v1'
HORIZON_MS = 7_200_000


def delta_micros(left, at, horizon, k=32, accepted=True):
    if k not in (32, 64) or any(type(x) is not int or not 0 <= x <= SCALE for x in (left, at, horizon)) or not left <= at <= horizon:
        raise ValueError('Invalid CDF or K')
    return (k // 2) * (SCALE - left - at) if accepted else -(k // 2) * horizon


def cdf_pair(curve, elapsed_ms):
    if type(elapsed_ms) is not int or not 0 <= elapsed_ms <= HORIZON_MS:
        raise ValueError('Time outside frozen horizon')
    times, values = curve['times_ms'], curve['cdf_ppm']
    before, through = bisect_left(times, elapsed_ms), bisect_right(times, elapsed_ms)
    return (values[before - 1] if before else 0, values[through - 1] if through else 0)


def select(artifact, rating_micros, difficulty):
    if not isinstance(rating_micros, int) or not isinstance(difficulty, int):
        raise ValueError('Invalid ratings')
    d = artifact['domain']
    if not d['rating_micros'][0] <= rating_micros <= d['rating_micros'][1] or not d['difficulty'][0] <= difficulty <= d['difficulty'][1]:
        raise ValueError('out_of_domain')
    delta = difficulty * SCALE - rating_micros
    matches = [c for c in artifact['curves'] if c['delta_min'] * SCALE <= delta < c['delta_max_exclusive'] * SCALE]
    if len(matches) != 1:
        raise ValueError('unsupported_region')
    return matches[0]


def isotonic_decreasing(values, weights):
    """Weighted PAVA; a new shape hypothesis, never a rewrite of source KM."""
    blocks = []
    for i, (v, w) in enumerate(zip(values, weights)):
        blocks.append([i, i + 1, v * w, w])
        while len(blocks) > 1 and blocks[-2][2] / blocks[-2][3] < blocks[-1][2] / blocks[-1][3]:
            b = blocks.pop(); a = blocks.pop()
            blocks.append([a[0], b[1], a[2] + b[2], a[3] + b[3]])
    out = [0.] * len(values)
    for start, end, total, weight in blocks:
        out[start:end] = [total / weight] * (end - start)
    return out
