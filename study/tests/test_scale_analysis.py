import csv
import hashlib
import math
from pathlib import Path
import tempfile
import unittest

import numpy as np

from lorem_study.aft import cdf
from lorem_study.baseline import km_fit,km_quantile
from lorem_study.io import StudyError, digest, read_json, write_json
from lorem_study.scale_analysis import (FrozenModels,analyze_scale,export_table,grouped_uncertainty,
                                       resampled_medians,support_reason,validate_plan,weighted_km)


class ScaleAnalysisTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        domain={k:[1,4000] if k!='delta' else [-4000,4000] for k in ('old_rating','problem_rating','delta')}
        common={'schema_version':1,'protocol_version':'sequential-v1','eligibility_version':'origin-20200701-v1','data_kind':'synthetic'}
        baseline={**common,'model_version':'kaplan-meier-v1','domain':domain,'delta_bin_width':400,
                  'global':km_fit([(7260,1)]),
                  'delta_bins':{'400':km_fit([(600,1)]),'-400':km_fit([(6000,1)])}}
        aft={**common,'model_version':'aft-v1','selection':{'status':'selected'},
             'fit':{'family':'lognormal','coefficients':[math.log(10),math.log(10)/10,0],
                    'center':[10,0],'scale':.5,'domain':domain,'max_followup_minutes':180}}
        self.inventory=[]
        for kind,obj in [('baseline',baseline),('aft',aft)]:
            path=self.root/f'{kind}.json'; write_json(path,obj)
            self.inventory.append({'kind':kind,'path':path.name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'canonical_sha256':digest(obj)})
        self.models=FrozenModels(self.root,self.inventory)

    def test_histogram_km_parity_ties_censoring_and_first_crossing(self):
        obs=[(10,1),(10,0),(20,1),(30,0)]
        actual=weighted_km([(1,10,1,1),(1,10,0,1),(2,20,1,1),(2,30,0,1)])
        expected=km_fit(obs)
        self.assertEqual(actual['table'],expected['table'])
        self.assertEqual(actual['t50_minutes'],km_quantile(expected,.5))
        self.assertEqual(weighted_km([(1,7260,1,1),(2,7300,0,1)])['t50_minutes'],121)
        self.assertIsNone(weighted_km([(1,10,0,10)])['t50_minutes'])
        self.assertIsNone(weighted_km([])['t50_minutes'])

    def test_mixture_invert_not_mean_of_medians_and_cdf_parity(self):
        cov=[(1000,1600,1),(2000,1600,1)]
        predictions=self.models.mixture(cov,180)
        expected=math.sqrt(10*100)
        self.assertAlmostEqual(predictions['aft']['t50_minutes'],expected,places=9)
        self.assertNotAlmostEqual(predictions['aft']['t50_minutes'],55,places=1)
        features=[{'old_rating':o,'delta':p-o} for o,p,_ in cov]
        self.assertAlmostEqual(cdf(self.models.aft,features,[expected]).mean(),.5,places=12)
        self.assertEqual(predictions['km_delta']['t50_minutes'],10)
        self.assertEqual(predictions['km_global']['t50_minutes'],121)

    def test_domain_missing_bins_no_fallback_and_short_followup(self):
        self.assertFalse(self.models.supported(1000,1000))  # delta 0 bin absent
        with self.assertRaisesRegex(StudyError,'unsupported covariates'):
            self.models.mixture([(1000,1000,1)],180)
        values=self.models.mixture([(1000,1600,1)],5)
        self.assertTrue(all(v['t50_minutes'] is None for v in values.values()))
        self.assertTrue(all(v['reason'] for v in values.values()))
        self.assertEqual(self.models.fixed(5000,[1600])[0]['reason'],'outside_model_domain')
        sql,params=self.models.common_sql()
        import sqlite3
        db=sqlite3.connect(':memory:'); self.addCleanup(db.close)
        db.execute('CREATE TABLE t(old_rating,problem_rating,delta)')
        db.executemany('INSERT INTO t VALUES (?,?,?)',[(2000,1600,-400),(2001,1600,-401),(1000,1600,600)])
        selected=list(db.execute('SELECT old_rating FROM t WHERE '+sql,params))
        self.assertEqual(selected,[(2000,),(1000,)])

    def test_group_draws_keep_multiplicity_missing_replicates_no_interval(self):
        hist=[(1,60,1,2),(2,120,0,2)]
        draws=np.asarray([[2,0],[0,2],[1,1]])
        result=grouped_uncertainty(hist,[1,2],draws)
        self.assertEqual(result['replicate_t50_minutes'],[1,None,1])
        self.assertIsNone(result['ci95_minutes'])
        self.assertEqual(result['missing_replicates'],1)
        self.assertEqual(result['unavailable_fraction'],1/3)
        self.assertIsNone(result['leave_one_group_out_t50_minutes']['1'])
        self.assertEqual(result['leave_one_group_out_t50_minutes']['2'],1)
        # A cell's absent global group still consumes draws, rather than local resampling.
        result=grouped_uncertainty([(1,60,1,2)],[1,2],np.asarray([[0,2],[2,0]]))
        self.assertEqual(result['replicate_t50_minutes'],[None,1])

    def test_duplicate_draw_weights_equal_expanded_observations(self):
        events=np.array([[1,1,0],[0,1,0]],dtype=float)
        totals=np.array([[1,1,1],[0,1,1]],dtype=float)
        values=resampled_medians(events,totals,np.array([60,120,180]),np.array([[2,1],[0,3]],dtype=float))
        obs=[(60,1),(120,1),(180,0)]*2+[(120,1),(180,0)]
        self.assertEqual(values[0],km_quantile(km_fit(obs),.5))
        self.assertEqual(values[1],2)

    def test_fixed_scenario_and_d012_do_not_claim_cdf(self):
        result=self.models.fixed(1000,[1600])
        self.assertTrue(all(v['mode']=='fixed_covariates' for v in result))
        d012=next(v for v in result if v['method']=='d012_operational')
        self.assertIsNone(d012['t50_minutes'])
        self.assertEqual(d012['duration_minutes'],120)
        independent=self.models.fixed(1000,[1000])
        self.assertAlmostEqual(independent[0]['t50_minutes'],10,places=10)
        self.assertEqual(independent[1]['t50_minutes'],121)
        self.assertIsNone(independent[2]['t50_minutes'])
        self.assertIn('no global fallback',independent[2]['reason'])
        with self.assertRaises(StudyError):self.models.fixed(0,[1600])

    def test_missing_or_mutated_model_fails_closed(self):
        (self.root/'aft.json').write_text('{}')
        with self.assertRaisesRegex(StudyError,'checksum'):
            FrozenModels(self.root,self.inventory)

    def test_synthetic_models_cannot_label_real_observations(self):
        plan=read_json(Path(__file__).parents[1]/'configs/scale-t50-plan-v1.json')
        prepared={'config_sha256':plan['config_sha256'],'data_kind':'real'}
        with self.assertRaisesRegex(StudyError,'Cannot mix real observations'):
            analyze_scale(self.root/'unused.sqlite',prepared,plan,self.models,self.root/'blocked')
        self.assertFalse((self.root/'blocked').exists())

    def test_prior_partition_summary_is_not_silently_treated_as_id_list(self):
        write_json(self.root/'prior-samples.json',{'samples':[{'selected_contest_ids':[1,2],
                    'partitions':{'test':{'contests':[1],'events':10}}}]})
        with self.assertRaisesRegex(StudyError,'explicit contest ID lists'):
            FrozenModels(self.root,self.inventory)

    def test_frozen_plan_and_support_contract(self):
        plan=read_json(Path(__file__).parents[1]/'configs/scale-t50-plan-v1.json')
        validate_plan(plan)
        plan['old_rating_edges']=[0,400,400]
        with self.assertRaises(StudyError):validate_plan(plan)
        self.assertEqual(support_reason({'attempts':0,'events':0,'groups':0},{'attempts':50,'events':20,'groups':5}),'no_observations')


if __name__=='__main__':unittest.main()
