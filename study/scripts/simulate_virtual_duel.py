"""Seeded synthetic trajectories under the exported generator; not external validation."""
import hashlib
import json
import math
import random
import statistics
import sys
from bisect import bisect_left
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from lorem_study.virtual_duel import SCALE, HORIZON_MS, cdf_pair, delta_micros, select
root = Path(__file__).resolve().parents[2]
raw = (root / 'app/src/main/assets/virtual-duel-v1.json').read_bytes(); artifact = json.loads(raw)
source = json.load(open(root / artifact['source']))
aft = json.load(open(root / 'study/results/scale-t50-v1/models/aft.json'))['fit']

def old_delta(rating, difficulty, elapsed, accepted):
    expectation = 1 / (1 + 10 ** ((difficulty-rating)/400))
    if not accepted:
        return -min(32, max(1, math.floor(8 + 24 * expectation + .5)))
    base = math.floor(8 + 32 * (1 - expectation) + .5)
    expected_ms = min(120, max(30, 60 + (difficulty-rating)/10)) * 60000
    factor = 1 if elapsed <= expected_ms else .5 if elapsed >= 2*expected_ms else 1-.5*(elapsed-expected_ms)/expected_ms
    return min(40, max(1, math.floor(base * factor + .5)))

# Source reference values distinguish the source KM from the new shape hypothesis.
reference = source['delta_bins']['400']['table']
def raw_pair(t):
    left = max((1-r['survival'] for r in reference if r['seconds']*1000 < t), default=0)
    at = max((1-r['survival'] for r in reference if r['seconds']*1000 <= t), default=0)
    return round(left*SCALE), round(at*SCALE)
parity = []
for r,d in [(800,800),(1200,1600),(1400,1000),(2000,2800)]:
    curve=select(artifact,r*SCALE,d); h=cdf_pair(curve,HORIZON_MS)[1]
    for k in (32,64):
        for t in (0,1,15000,900000,1800000,3600000,5400000,HORIZON_MS-1,HORIZON_MS):
            left,at=cdf_pair(curve,t)
            for accepted in (False,True):
                parity.append(dict(rating_micros=r*SCALE,difficulty=d,k=k,elapsed_ms=t,accepted=accepted,
                    left=left,at=at,horizon=h,delta_micros=delta_micros(left,at,h,k,accepted)))
trajectories=[]
for initial in (800,1400,2000):
 for k in (32,64):
  for slowdown in (1.,1.25):
   finals=[]; legacy_finals=[]; unavailable=0
   for replica in range(30):
    rng=random.Random(20261023+replica); rating=initial*SCALE; legacy=initial
    for attempt in range(300):
        difficulty=max(800,min(3000,round(rating/SCALE/100)*100))
        try:
            predicted=select(artifact,rating,difficulty); truth=select(artifact,1400*SCALE,difficulty)
        except ValueError:
            unavailable+=1; break
        u=rng.randrange(SCALE); idx=bisect_left(truth['cdf_ppm'],u+1)
        elapsed=math.ceil(truth['times_ms'][idx]*slowdown) if idx<len(truth['times_ms']) else HORIZON_MS+1
        accepted=elapsed<=HORIZON_MS; elapsed=min(elapsed,HORIZON_MS)
        left,at=cdf_pair(predicted,elapsed); h=cdf_pair(predicted,HORIZON_MS)[1]
        rating+=delta_micros(left,at,h,k,accepted)
        legacy=max(0,legacy+old_delta(legacy,difficulty,elapsed,accepted))
    finals.append(rating/SCALE); legacy_finals.append(legacy)
   trajectories.append(dict(initial=initial,k=k,slowdown=slowdown,true_skill=1400,replicas=30,attempts=300,
       mean_final=statistics.mean(finals),sd_final=statistics.stdev(finals),legacy_mean_same_problems=statistics.mean(legacy_finals),
       unsupported_trajectories=unavailable))
comparison=[]
for r,d in [(800,800),(1200,1600),(2000,2400),(1400,1399),(1400,1400)]:
 c=select(artifact,r*SCALE,d)
 mu=aft['coefficients'][0]+sum((x-center)*coef for x,center,coef in zip([r/100,(d-r)/100],aft['center'],aft['coefficients'][1:]))
 comparison.append(dict(rating=r,difficulty=d,km_derived_F120=cdf_pair(c,HORIZON_MS)[1]/SCALE,
     smooth_aft_F120=.5*(1+math.erf((math.log(120)-mu)/aft['scale']/math.sqrt(2)))))
result=dict(seed=20261023,artifact_sha256=hashlib.sha256(raw).hexdigest(),parity_cases=parity,trajectories=trajectories,
    source_km_K64=[dict(minutes=t//60000,delta=delta_micros(*raw_pair(t),raw_pair(HORIZON_MS)[1],64,True)/SCALE) for t in (900000,1800000,3600000,5400000,HORIZON_MS)],
    comparison_with_frozen_smooth_aft=comparison,note='Synthetic same-generator recovery only; bin plateaus prevent unique skill identification. D012 follows same selected problems. No independent or pedagogical validation.')
(root/'study/results/virtual-duel-v1/scenarios.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(dict(trajectories=trajectories,comparison=comparison),indent=2))
