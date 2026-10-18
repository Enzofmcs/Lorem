from contextlib import redirect_stdout
import io
from pathlib import Path
import tempfile
import unittest
import uuid

from lorem_study.cli import main
from lorem_study.io import StudyError, digest, read_json, write_json
from lorem_study.ipsum import PROTOCOL, prepare_ipsums, quality


def fixture(participant=1, n=20):
    rows=[]
    for j in range(n):
        start=1593561600000+100000+j*60000
        rows.append({'sample_id':digest([participant,j]),'problem_contest_id':101,'problem_index':'A',
                     'started_at_epoch_ms':start,'ended_at_epoch_ms':start+60000,
                     'first_ac_at_epoch_ms':start+60000 if j%2==0 else None,'status':'COMPLETED' if j%2==0 else 'PENDING',
                     'upsolve_ac_at_epoch_ms':None,'cf_rating_before':1200,'cf_rating_observed_at_epoch_ms':start-1000,
                     'cf_rating_source':'codeforces:user.info','lorem_rating_before':1300,'problem_rating':1400,
                     'recording_version':'ipsum-study-v1','eligibility_version':'origin-20200701-v1',
                     'origin_started_at_epoch_ms':1593561600000,'origin_verified_at_epoch_ms':start-2000,
                     'origin_source':'codeforces:contest.list','hint_revealed_at_epoch_ms':None,'hint_used':False,
                     'interruptions':None,'wall_clock_only':True})
    return {'schema_version':1,'export_version':'ipsum-local-v1','data_kind':'synthetic',
            'participant_key':str(uuid.UUID(int=participant)),'attempts':rows}


def metadata(cid=101):
    return {'contest':{'id':cid,'type':'CF','phase':'FINISHED','name':'Codeforces Round (Div. 2)','startTimeSeconds':1593561600},
            'problems':[{'contestId':cid,'index':c} for c in 'ABC']}


def fetch(cid):
    return metadata(cid),{'data_kind':'synthetic','sha256':digest(metadata(cid))}


class IpsumTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)

    def inputs(self,count=1):
        paths=[]
        for i in range(1,count+1):
            p=self.root/f'export-{i}.json';write_json(p,fixture(i));paths.append(p)
        return paths

    def test_actual_android_serializer_fixture_is_accepted_without_reinterpretation(self):
        fixture_path=Path(__file__).parent/'fixtures/ipsum-export-android-v1.json'
        result=prepare_ipsums(self.root,[fixture_path],metadata_fetch=fetch)
        self.assertEqual(result['data_kind'],'synthetic')
        self.assertEqual((result['valid_attempts'],result['events'],result['censored']),(1,1,0))
        self.assertEqual(result['status'],'insufficient_data')

    def test_no_data_is_insufficient_and_never_opens_test_or_requests_network(self):
        result=prepare_ipsums(self.root,offline=True)
        self.assertEqual((result['data_kind'],result['status']),('not_collected','insufficient_data'))
        self.assertEqual(result['network_requests'],0)
        self.assertFalse(result['partitions_created'])
        self.assertIn('CLOSED',result['integration_gate'])
        out=io.StringIO()
        with redirect_stdout(out):self.assertEqual(main(['prepare-ipsums','--work',str(self.root),'--offline']),0)

    def test_participant_split_replay_dedup_and_frozen_changes(self):
        paths=self.inputs(10)
        first=prepare_ipsums(self.root,paths,metadata_fetch=fetch)
        self.assertEqual((first['valid_attempts'],first['events'],first['censored']),(200,100,100))
        self.assertEqual(digest(first),digest(prepare_ipsums(self.root,paths+paths,metadata_fetch=fetch)))
        partitions=read_json(self.root/'ipsum/partitions.json')['participants']
        self.assertEqual({k:len(v) for k,v in partitions.items()},{'train':6,'validation':2,'test':2})
        flat=[p for values in partitions.values() for p in values]
        self.assertEqual(len(set(flat)),10)
        edited=read_json(paths[0]);edited['attempts'][0]['cf_rating_before']+=1;write_json(paths[0],edited)
        with self.assertRaisesRegex(StudyError,'already frozen'):prepare_ipsums(self.root,paths,metadata_fetch=fetch)

    def test_any_easy_hard_pair_or_special_excludes_the_whole_contest(self):
        paths=self.inputs()
        for case in ('versions','special'):
            data=metadata()
            if case=='versions':data['problems'] += [{'contestId':101,'index':c} for c in ('D1','D2')]
            else:data['contest']['name']='April Fools Round (Div. 2)'
            result=prepare_ipsums(self.root/case,paths,metadata_fetch=lambda cid:(data,{'data_kind':'synthetic'}))
            self.assertEqual(result['valid_attempts'],0)
            self.assertEqual(result['excluded'],20)

    def test_legacy_future_cf_missing_origin_hint_and_upsolve_are_not_imputed(self):
        row=fixture()['attempts'][0]
        legacy={**row,'cf_rating_before':None,'cf_rating_observed_at_epoch_ms':None,'current_cf_rating':3000,
                'recording_version':None}
        self.assertIn('missing_prior_cf_snapshot',quality(legacy,metadata()))
        self.assertIn('legacy_recording_fields_unknown',quality(legacy,metadata()))
        self.assertIn('cf_snapshot_after_start',quality({**row,'cf_rating_observed_at_epoch_ms':row['ended_at_epoch_ms']},metadata()))
        self.assertIn('missing_or_ineligible_origin_snapshot',quality({**row,'origin_started_at_epoch_ms':1593561599999},metadata()))
        self.assertIn('hint_time_outside_window',quality({**row,'hint_revealed_at_epoch_ms':1,'hint_used':True},metadata()))
        censored={**row,'status':'SOLVED_OUTSIDE','first_ac_at_epoch_ms':None,'upsolve_ac_at_epoch_ms':row['ended_at_epoch_ms']+1000}
        self.assertEqual(quality(censored,metadata()),[])
        self.assertIn('upsolve_is_not_ipsum_event',quality({**censored,'first_ac_at_epoch_ms':censored['upsolve_ac_at_epoch_ms']},metadata()))

    def test_conflicting_snapshots_and_live_synthetic_requests_are_rejected(self):
        paths=self.inputs()
        with self.assertRaisesRegex(StudyError,'never live API'):prepare_ipsums(self.root,paths)
        duplicate=read_json(paths[0]);duplicate['attempts'][0]['ended_at_epoch_ms']+=1
        second=self.root/'duplicate.json';write_json(second,duplicate)
        with self.assertRaisesRegex(StudyError,'Conflicting'):prepare_ipsums(self.root,paths+[second],metadata_fetch=fetch)

    def test_api_failure_stops_instead_of_fabricating_negative_or_censored_rows(self):
        def failed(cid):raise StudyError('offline cache miss')
        with self.assertRaisesRegex(StudyError,'offline cache miss'):prepare_ipsums(self.root,self.inputs(),metadata_fetch=failed)
        self.assertFalse((self.root/'ipsum/manifest.json').exists())
