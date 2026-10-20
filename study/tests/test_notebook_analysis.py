"""Synthetic rule/integration tests, never empirical evidence."""
from copy import deepcopy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from lorem_study.baseline import km_fit, split, train_baseline
from lorem_study.config import load_config
from lorem_study.fixture import create_fixture
from lorem_study.io import StudyError, read_json, read_rows, write_json, write_rows
from lorem_study.notebook_analysis import (budget_outcomes, classify_time, cluster_bootstrap,
    delta_bin, freeze_plan, funnel, grid, load_attempts, observations, rating_bin,
    select_region, stability, summarize_curve, time_bands, verify_snapshot)
from lorem_study.notebook_session import Session
from lorem_study.prepare import prepare

STUDY = Path(__file__).resolve().parents[1]


def plan():
    value = read_json(STUDY / 'configs/analysis-time-bands-v2.json')
    value['display_support'] = {'attempts': 1, 'events': 1, 'groups': 1}
    value['bootstrap_replicates'] = 30
    return value


def row(i=0, *, t=600, event=1, old=1200, problem=1600, group='a', contest=1):
    return {'sample_id':str(i), 'participant_key':str(i), 'problem_contest_id':contest,
            'problem_index':'A','contest_id':contest,'old_rating':old,'problem_rating':problem,
            'delta':problem-old,'group':group,'partition':'train','quality':'valid','reason':None,
            'duration_seconds':t,'event':event,'start_seconds':0,'anchor_seconds':60,
            'observed_end_seconds':60+t,'end_seconds':10000,
            'sensitivity_last_submission_seconds':t-60}


class AnalysisTests(unittest.TestCase):
    def test_bins_include_lower_exclude_upper_and_negative_delta(self):
        self.assertEqual(rating_bin(1200,[800,1200,1600]),1200)
        self.assertEqual(rating_bin(1199,[800,1200,1600]),800)
        self.assertIsNone(rating_bin(1600,[800,1200,1600]))
        self.assertIsNone(rating_bin(0,[0,400]))
        self.assertIsNone(rating_bin(True,[0,400]))
        self.assertEqual([delta_bin(x) for x in [-401,-400,-1,0,399,400]],[-800,-400,-400,0,0,400])

    def test_km_tied_event_and_censor_has_known_quartiles_and_no_imputed_p75(self):
        curve=km_fit([(600,1),(600,0),(1200,1),(1800,0)])
        bands=time_bands(curve,30)
        self.assertEqual(bands['T1']['minutes'],10)
        self.assertEqual(bands['T2']['minutes'],20)
        self.assertIsNone(bands['T3']['minutes'])
        self.assertEqual(bands['T3']['reason'],'quantile_not_reached')
        self.assertAlmostEqual(bands['p_ac_by_budget'],.625)
        self.assertEqual(bands['T4']['kind'],'operational_budget_not_P100')

    def test_tied_quartiles_and_budget_incompatibility_preserve_limits(self):
        bands=time_bands(km_fit([(600,1)]*4),5)
        self.assertTrue(bands['empty_bands_from_ties'])
        self.assertTrue(bands['budget_incompatible'])
        self.assertEqual(bands['T3']['minutes'],10)
        self.assertEqual(bands['T3']['reason'],'exceeds_budget')

    def test_inclusive_budget_and_censoring_are_separate(self):
        bands=time_bands(km_fit([(60,1),(120,1),(180,1),(240,1)]),120)
        self.assertEqual(classify_time(120,1,bands),'slow_within_budget')
        self.assertEqual(classify_time(120.01,1,bands),'ac_after_budget')
        self.assertEqual(classify_time(119,0,bands),'censored_before_budget_unknown_outcome')
        rows=[row(0,t=7200),row(1,t=7201),row(2,t=7100,event=0),row(3,t=7200,event=0)]
        self.assertEqual(budget_outcomes(rows,120),{'ac_by_h':1,'ac_after_h':1,'censored_before_h':1,'censored_at_or_after_h':1})

    def test_total_censoring_and_insufficient_followup(self):
        p=plan();p['display_support']['events']=1
        summary=summarize_curve([row(event=0)],p,apply_filter=False)
        bands=time_bands(summary['curve'],120)
        self.assertIsNone(bands['T1']['minutes'])
        self.assertIsNone(bands['p_ac_by_budget'])
        self.assertEqual(summary['probabilities']['120']['reason'],'insufficient_followup')

    def test_no_fallback_for_sparse_empty_and_outside_cell(self):
        p=plan();p['display_support']['attempts']=2
        result=grid([row()],p)
        cell=next(c for c in result['cells'] if c['x']==1200 and c['y']==1600)
        self.assertEqual(cell['reason'],'below_display_support')
        self.assertTrue(any(c['reason']=='empty_cell' for c in result['cells']))
        self.assertEqual(select_region([row()],p,4000,1600),([], 'outside_grid'))

    def test_two_dimensional_bins_do_not_pool_same_delta(self):
        data=[row(0,t=600),row(1,t=1200,old=1600,problem=2000)]
        result=grid(data,plan())
        found=[c for c in result['cells'] if c['support']['attempts']]
        self.assertEqual([c['median_minutes'] for c in found],[10,20])
        self.assertEqual(result['delta'][0]['support']['attempts'],2)

    def test_at_risk_includes_tie_and_scope_is_train_only(self):
        data=[row(t=1800),{**row(1,t=7200),'partition':'test'}]
        p=plan();p['scope']='train'
        cell=next(c for c in grid(data,p)['cells'] if c['support']['attempts'])
        self.assertEqual(cell['support']['at_risk'],{'30':1,'60':0,'120':0})
        self.assertEqual(cell['median_minutes'],30)

    def test_funnel_accounts_excluded_without_double_counting_distinct_participants(self):
        data=[row(),{**row(1,event=0),'participant_key':'0'},
              {**row(2),'quality':'excluded','reason':'predecessor_not_valid','event':None}]
        result=funnel(data)
        self.assertEqual(result['total']['observed_attempts'],3)
        self.assertEqual(result['total']['participants'],2)
        self.assertEqual(result['total']['valid_events'],1)
        self.assertEqual(result['total']['valid_censored'],1)
        self.assertEqual(sum(x['observed_attempts'] for x in result['old_rating'].values()),3)

    def test_other_estimands_do_not_mutate_labels_or_impute_unknown_times(self):
        rows=[row(),row(1,event=0),{**row(2,event=0),'sensitivity_last_submission_seconds':None}]
        original=deepcopy(rows)
        changed,missing=observations(rows,'last_submission')
        self.assertEqual([r['duration_seconds'] for r in changed],[600,540])
        self.assertEqual(missing,1)
        changed,_=observations(rows,'since_contest_start')
        self.assertEqual(changed[0]['duration_seconds'],660)
        self.assertEqual(rows,original)

    def test_bootstrap_seed_multiplicity_and_unidentified_replicates(self):
        data=[row(0,t=600,group='a'),row(1,t=1200,event=0,group='b')]
        p=plan()
        result=cluster_bootstrap(data,p,20)
        self.assertEqual(result,cluster_bootstrap(data,p,20))
        self.assertGreater(result['statistics']['p75_minutes']['missing_fraction'],0)
        self.assertIsNone(result['statistics']['p75_minutes']['ci95'])
        # A drawn twice produces F(20)=1, B twice F(20)=0; retaining multiplicity matters.
        probabilities={r['p_ac'] for r in result['replicates']}
        self.assertTrue({0,.5,1}.issubset(probabilities))
        p['seed']+=1
        self.assertNotEqual(result['draws_sha256'],cluster_bootstrap(data,p,20)['draws_sha256'])

    def test_one_group_does_not_fabricate_precision(self):
        result=cluster_bootstrap([row()],plan(),120)
        self.assertEqual(result['reason'],'fewer_than_two_groups')
        self.assertEqual(result['statistics'],{})

    def test_stability_respects_support_and_reports_repeated_people(self):
        p=plan()
        data=[row(),{**row(1,t=1200,group='b',contest=2),'participant_key':'0'}]
        result=stability(data,p,1200,1600,120)
        self.assertEqual(result['participants_repeated_across_groups'],1)
        self.assertEqual(len(result['leave_one_group_out']),2)
        self.assertEqual(set(result['estimand_sensitivity']),set(p['estimands']))

    def test_plan_freezes_before_observations(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'plan.json';p=plan();freeze_plan(path,p)
            changed=deepcopy(p);changed['budget_minutes']=60
            with self.assertRaisesRegex(StudyError,'frozen'):
                freeze_plan(path,changed)


class NotebookDataTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.root=Path(self.temp.name)/'snapshot'
        create_fixture(self.root,load_config())
        prepare(self.root);split(self.root);train_baseline(self.root)

    def tearDown(self):
        self.temp.cleanup()

    def test_real_pipeline_join_reconciles_synthetic_totals(self):
        manifest,_,rows=load_attempts(self.root)
        self.assertEqual(len(rows),90)
        self.assertEqual(funnel(rows)['total']['valid_events'],40)
        self.assertEqual(funnel(rows)['total']['valid_censored'],10)
        self.assertEqual(manifest['observed_attempts'],90)

    def test_duplicate_or_missing_audit_refused(self):
        path=self.root/'prepared/audit.jsonl';rows=list(read_rows(path))
        write_rows(path,rows[:-1])
        with self.assertRaisesRegex(StudyError,'identities mismatch'):
            load_attempts(self.root)
        write_rows(path,rows+[rows[0]])
        with self.assertRaisesRegex(StudyError,'Duplicate'):
            load_attempts(self.root)

    def test_inconsistent_audit_seconds_refused(self):
        path=self.root/'prepared/audit.jsonl';rows=list(read_rows(path))
        valid=next(r for r in rows if r['quality']=='valid');valid['anchor_seconds']+=1
        write_rows(path,rows)
        with self.assertRaisesRegex(StudyError,'units'):
            load_attempts(self.root)

    def test_raw_corruption_detected_before_analysis(self):
        path=next((self.root/'raw').iterdir());path.write_bytes(b'corrupt')
        with self.assertRaisesRegex(StudyError,'checksum'):
            verify_snapshot(self.root)

    def test_aggregate_mode_no_network_and_historical_sources_unchanged(self):
        from lorem_study.notebook_analysis import file_sha
        path=STUDY/'results/2026-10-18/report/demo.json';sha=file_sha(path)
        with patch('socket.socket.connect',side_effect=AssertionError('network forbidden')):
            session=Session(STUDY,output=Path(self.temp.name)/'out')
        self.assertIsNone(session.rows)
        self.assertFalse(session.source['full_snapshot_recovered'])
        self.assertAlmostEqual(session.historical['demo']['km_delta']['probabilities'][-1]['p_ac'],.6220994714114649)
        self.assertEqual(sha,file_sha(path))
        export=read_json(session.output/'inputs.json')
        self.assertEqual(export['analysis_version'],'analysis-time-bands-v2')
        self.assertIn('grouping',export)

    def test_missing_full_snapshot_does_not_silently_fall_back(self):
        with self.assertRaises(FileNotFoundError):
            Session(STUDY,mode='full',work=Path(self.temp.name)/'absent',output=Path(self.temp.name)/'out')
        with self.assertRaisesRegex(StudyError,'Explicit work'):
            Session(STUDY,mode='full',output=Path(self.temp.name)/'out2')

    def test_historical_directories_protected(self):
        with self.assertRaisesRegex(StudyError,'historical'):
            Session(STUDY,output=STUDY/'results/2026-10-18/new')


if __name__=='__main__':
    unittest.main()


class SnapshotRestoreTests(unittest.TestCase):
    @staticmethod
    def restore_function():
        import importlib.util
        spec = importlib.util.spec_from_file_location('restore_snapshot', STUDY/'scripts/restore_notebook_snapshot.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module.restore

    def make_archive(self, path, name='snapshot/marker.json', link=False):
        import io, tarfile, hashlib
        with tarfile.open(path,'w:gz') as archive:
            member=tarfile.TarInfo(name)
            data=b'{"data_kind":"synthetic"}'
            if link:
                member.type=tarfile.SYMTYPE;member.linkname='/tmp/unsafe-target'
                archive.addfile(member)
            else:
                member.size=len(data);archive.addfile(member,io.BytesIO(data))
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def test_checksum_verified_restore_and_no_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);archive=root/'synthetic.tar.gz'
            sha=self.make_archive(archive)
            restore=self.restore_function()
            with self.assertRaisesRegex(ValueError,'checksum'):
                restore(archive,root/'bad','0'*64)
            self.assertFalse((root/'bad').exists())
            restore(archive,root/'new',sha)
            self.assertTrue((root/'new/snapshot/marker.json').exists())
            with self.assertRaisesRegex(ValueError,'must not exist'):
                restore(archive,root/'new',sha)

    def test_path_escape_and_symbolic_links_are_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            for index,(name,link) in enumerate([('../escape.json',False),('link',True)]):
                archive=root/f'synthetic{index}.tar.gz';sha=self.make_archive(archive,name,link)
                with self.assertRaisesRegex(ValueError,'Unsafe'):
                    self.restore_function()(archive,root/f'out{index}',sha)
                self.assertFalse((root/f'out{index}').exists())
