"""Offline export of frozen full KM curves; never acquires or fits source data."""
import hashlib
import json
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lorem_study.virtual_duel import isotonic_decreasing, SCALE, HORIZON_MS

root = Path(__file__).resolve().parents[2]
source = root / 'study/results/scale-t50-v1/models/baseline.json'
raw = source.read_bytes(); model = json.loads(raw)
# Exclude bins with censored follow-up before H. Complete resolution permits a
# terminal CDF=1, but those extreme bins are deliberately outside this prototype.
bins = [(int(k), v) for k, v in model['delta_bins'].items() if v['max_followup_seconds'] >= 7200 and v['n'] >= 50 and v['events'] >= 20]
bins.sort()
raw_cdfs = []
for _, c in bins:
    values = []; j = 0; f = 0
    for second in range(7201):
        while j < len(c['table']) and c['table'][j]['seconds'] <= second:
            f = 1 - c['table'][j]['survival']; j += 1
        values.append(f)
    raw_cdfs.append(values)
projected = [isotonic_decreasing(list(v), [c['n'] for _, c in bins]) for v in zip(*raw_cdfs)]
curves = []
for i, (low, c) in enumerate(bins):
    times, values = [], []
    previous = 0
    for second, row in enumerate(projected):
        value = int(row[i] * SCALE + .5)
        if value != previous:
            times.append(second * 1000); values.append(value); previous = value
    curves.append(dict(delta_min=low, delta_max_exclusive=low + 400, attempts=c['n'], events=c['events'],
                       followup_ms=c['max_followup_seconds'] * 1000, times_ms=times, cdf_ppm=values))
artifact = dict(policy='virtual-duel-v1', model_version='km-delta-isotonic-20261020-v1',
    data_kind='real-derived-experimental', source='study/results/scale-t50-v1/models/baseline.json',
    source_sha256=hashlib.sha256(raw).hexdigest(), training_sha256=model['training_sha256'],
    population='20/10 earlier 175213 training attempts; not the expanded 4754352 sample',
    hypothesis='weighted isotonic CDF across delta bins; no continuous R/D interpolation',
    horizon_ms=HORIZON_MS, probability_scale=SCALE, domain=dict(rating_micros=[21*SCALE,3857*SCALE], difficulty=[800,3500]), curves=curves)
out = root / 'app/src/main/assets/virtual-duel-v1.json'
out.write_text(json.dumps(artifact, separators=(',', ':')) + '\n')
sha = hashlib.sha256(out.read_bytes()).hexdigest()
# Diagnose rather than hide bin crossings or call the projection validated.
violations = sum(a < b for row in zip(*raw_cdfs) for a,b in zip(row,row[1:]))
report = dict(artifact_sha256=sha, source_sha256=artifact['source_sha256'], raw_bin_order_violations=violations,
    max_projection_probability_change=max(abs(projected[t][i]-raw_cdfs[i][t]) for t in range(7201) for i in range(len(bins))),
    bins=[c['delta_min'] for c in curves], excluded_bins=[int(k) for k in model['delta_bins'] if int(k) not in [x[0] for x in bins]],
    note='Projection is a new hypothesis; flat within bins, jumps at boundaries. No claim of superiority or CF/Lorem equivalence.')
(root / 'study/results/virtual-duel-v1/model.json').write_text(json.dumps(report, indent=2)+'\n')
print(json.dumps(report, indent=2))
