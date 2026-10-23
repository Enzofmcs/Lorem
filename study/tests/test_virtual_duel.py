import hashlib
import json
import unittest
from pathlib import Path
from lorem_study.virtual_duel import *

ROOT = Path(__file__).resolve().parents[2]
class VirtualDuelTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw=(ROOT/'app/src/main/assets/virtual-duel-v1.json').read_bytes()
        cls.a=json.loads(cls.raw)
    def test_export_hash_and_source_are_frozen(self):
        report=json.loads((ROOT/'study/results/virtual-duel-v1/model.json').read_text())
        self.assertEqual(report['artifact_sha256'],hashlib.sha256(self.raw).hexdigest())
        self.assertEqual(self.a['source_sha256'],hashlib.sha256((ROOT/self.a['source']).read_bytes()).hexdigest())
    def test_cdf_and_score_order_and_exact_expected_balance(self):
        for c in self.a['curves']:
            self.assertGreaterEqual(c['followup_ms'],HORIZON_MS)
            self.assertEqual(c['times_ms'],sorted(set(c['times_ms'])))
            self.assertEqual(c['cdf_ppm'],sorted(c['cdf_ppm']))
            for k in (32,64):
                expectation=0; previous=0; last_delta=k*SCALE//2
                h=cdf_pair(c,HORIZON_MS)[1]
                failure=delta_micros(0,0,h,k,False)
                for at in c['cdf_ppm']:
                    delta=delta_micros(previous,at,h,k,True)
                    self.assertGreaterEqual(delta,failure)
                    self.assertLessEqual(delta,last_delta)
                    self.assertLessEqual(abs(delta),k*SCALE//2)
                    expectation+=(at-previous)*delta
                    previous=at;last_delta=delta
                expectation+=(SCALE-h)*failure
                self.assertEqual(0,expectation)
        for second in range(0,7201,15):
            fs=[cdf_pair(c,second*1000)[1] for c in self.a['curves']]
            self.assertEqual(fs,sorted(fs,reverse=True))
    def test_ties_horizon_and_zero_and_one(self):
        c=dict(times_ms=[1000,2000],cdf_ppm=[200000,1000000])
        self.assertEqual((0,200000),cdf_pair(c,1000))
        self.assertEqual((200000,200000),cdf_pair(c,1001))
        self.assertEqual(0,delta_micros(0,0,0,32,False))
        self.assertEqual(-16000000,delta_micros(SCALE,SCALE,SCALE,32,True))
        for t in (-1,HORIZON_MS+1,float('nan')):
            with self.assertRaises(ValueError):cdf_pair(c,t)
        for x in [(-1,0,SCALE),(0,SCALE+1,SCALE),(1,0,SCALE),(0,0,-1)]:
            with self.assertRaises(ValueError):delta_micros(*x)
    def test_domain_bins_and_fractional_rating(self):
        self.assertEqual(0,select(self.a,800*SCALE,1199)['delta_min'])
        self.assertEqual(400,select(self.a,800*SCALE,1200)['delta_min'])
        self.assertEqual(0,select(self.a,800*SCALE+1,1200)['delta_min'])
        for r,d in [(0,800),(4000*SCALE,800),(800*SCALE,3501),(3000*SCALE,800)]:
            with self.assertRaises(ValueError):select(self.a,r,d)
    def test_android_persisted_demo_replays_exactly_with_next_input(self):
        r=json.loads((ROOT/'study/results/virtual-duel-v1/sdk-demo.json').read_text())
        self.assertEqual(r['artifact_sha256'],hashlib.sha256(self.raw).hexdigest())
        c=select(self.a,r['rating_micros'],r['difficulty'])
        left,at=cdf_pair(c,r['elapsed_ms'])
        self.assertEqual(r['delta_micros'],delta_micros(left,at,cdf_pair(c,HORIZON_MS)[1],r['k'],r['accepted']))
        self.assertEqual(r['after_micros'],r['rating_micros']+r['delta_micros'])
        self.assertEqual(r['after_micros'],r['restored_next_input_micros'])
    def test_saved_fixtures_reproduce_model_and_scoring(self):
        cases=json.loads((ROOT/'study/results/virtual-duel-v1/scenarios.json').read_text())['parity_cases']
        for c in cases:
            curve=select(self.a,c['rating_micros'],c['difficulty'])
            left,at=cdf_pair(curve,c['elapsed_ms'])
            self.assertEqual(c['delta_micros'],delta_micros(left,at,cdf_pair(curve,HORIZON_MS)[1],c['k'],c['accepted']))
