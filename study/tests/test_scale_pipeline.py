import gzip
import io
from pathlib import Path
import tempfile
import unittest

from lorem_study.api import ApiClient
from lorem_study.collect import collect
from lorem_study.config import load_config
from lorem_study.fixture import fixture_api
from lorem_study.io import StudyError, canonical, digest, put_bytes, raw_json, read_json, read_rows, write_json
from lorem_study.prepare import prepare
from lorem_study.scale_collect import BoundedTransport, BudgetExceeded, freeze_scale_selection, freeze_universe, import_cache, replay_scale
from lorem_study.scale_prepare import connect, file_sha, ingest_partition, prepare_scale, shared_groups, structure_origin_links
from lorem_study.scale_delivery import coverage_report


class ScalePipelineTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.config = load_config()
        self.config["max_screened_candidates"] = 5000
        self.limits = {"max_network_attempts":4,"max_raw_compressed_bytes":10**9,
                       "min_free_disk_bytes":0,"max_response_bytes":1000}

    def test_group_coverage_counts_distinct_people_and_shared_problems_once(self):
        prepared_root=self.root/'analysis';prepared_root.mkdir()
        db=connect(prepared_root/'attempts.sqlite')
        records=[('a',1,'p',1,'A',1),('b',1,'q',1,'B',0),('c',2,'p',1,'A',1)]
        for sid,cid,person,origin,index,event in records:
            db.execute('''INSERT INTO attempts(sample_id,contest_id,participant,problem_contest_id,
              problem_index,old_rating,problem_rating,delta,quality,duration,event,group_id)
              VALUES(?,?,?,?,?,1200,1600,400,'valid',60,?,1)''',(sid,cid,person,origin,index,event))
        db.execute('CREATE INDEX idx_contest ON attempts(contest_id)')
        db.commit();db.close()
        snapshot={'data_kind':'synthetic','selection':{'contest_ids':[1,2],'shortfall':398},
                  'coverage':{'complete':2},'config':{'target_contests':400},
                  'contests':{'1':{'complete':True},'2':{'complete':True}}}
        prepared={'data_kind':'synthetic','snapshot_sha256':digest(snapshot),'selection_sha256':'s',
                  'database_sha256':file_sha(prepared_root/'attempts.sqlite'),'database_bytes':0,
                  'groups':[[1,2]],'eligible_contest_ids':[1,2],'participants_repeated_across_groups':0,
                  'maximum_worker_rss_bytes':None,'observed_attempts':3,'valid_events':2,'valid_censored':1,'excluded':0,
                  'partitions':[{'contest_id':cid,'prepared':{'contests':[{'contest_id':cid}]},
                                 'source_sha256':'x','peak_rss_bytes':None,'streams':{},'seconds':0} for cid in [1,2]]}
        for name,value in [('snapshot',snapshot),('universe',{'metadata_eligible':2,'regular':2,'upper_bound_before_standings':2}),
                           ('screening',{'decisions':[{'reason':None}]}),
                           ('network-budget',{'first_start_utc':'2026-10-20T00:00:00Z','last_response_utc':'2026-10-20T00:00:00Z',
                                              'attempts':0,'http_bytes':0,'minimum_start_gap_seconds':None})]:
            write_json(self.root/f'{name}.json',value)
        write_json(prepared_root/'manifest.json',prepared)
        analysis={'data_kind':'synthetic','snapshot_sha256':digest(snapshot),'plan_sha256':'p',
                  'rows':[{'observed_attempts':3}],'outside_grid_observed':0,'overlap_with_examined_models':{}}
        report=coverage_report(self.root,prepared_root,analysis)
        group=report['by_group'][0]
        self.assertEqual((group['observed_attempts'],group['participants_valid_unique'],group['problems_valid_unique']),(3,2,2))
        self.assertEqual(group['contests_with_observations'],2)
        self.assertEqual(group['groups_with_observations'],1)
        self.assertEqual(sum(c['participants_valid_unique'] for c in report['by_contest']),3)

    def test_compressed_legacy_parity_integrity_and_atomic_objects(self):
        body = canonical({"status":"OK","result":[1,2]})
        sha = put_bytes(self.root,body,compress=True)
        ref = {"sha256":sha}
        self.assertEqual(raw_json(self.root,ref),[1,2])
        first = (self.root/'raw'/f'{sha}.json.gz').read_bytes()
        put_bytes(self.root,body,compress=True)
        self.assertEqual((self.root/'raw'/f'{sha}.json.gz').read_bytes(),first)
        (self.root/'raw'/f'{sha}.json.gz').write_bytes(b'broken')
        with self.assertRaises(StudyError):
            raw_json(self.root,ref)
        put_bytes(self.root,body)
        self.assertEqual(raw_json(self.root,ref),[1,2])
        (self.root/'raw'/f'{sha}.json').unlink()
        (self.root/'raw'/f'{sha}.json.gz').write_bytes(gzip.compress(b'{}'))
        with self.assertRaisesRegex(StudyError,'checksum'):
            raw_json(self.root,ref)
        self.assertFalse(list((self.root/'raw').glob('*.tmp')))

    def test_durable_network_gate_attempt_budget_and_oversize(self):
        clock = [10.]
        calls = []
        class Response(io.BytesIO):
            code,headers = 200,{}
        def opener(*args,**kwargs):
            calls.append(clock[0])
            return Response(b'{}')
        def sleep(t): clock[0] += t
        kw = dict(clock=lambda:clock[0],sleep=sleep,opener=opener)
        BoundedTransport(self.root,self.limits,**kw)('https://example.invalid',1)
        BoundedTransport(self.root,self.limits,**kw)('https://example.invalid',1)
        self.assertEqual(calls,[10,12])
        limits = {**self.limits,'max_network_attempts':2}
        with self.assertRaisesRegex(BudgetExceeded,'attempt budget'):
            BoundedTransport(self.root,limits,**kw)('unused',1)
        self.assertEqual(read_json(self.root/'network-budget.json')['attempts'],2)
        limits = {**self.limits,'max_response_bytes':1}
        with self.assertRaisesRegex(BudgetExceeded,'per-response'):
            BoundedTransport(self.root,limits,**kw)('https://example.invalid',1)
        self.assertEqual(read_json(self.root/'network-budget.json')['attempts'],3)

    def test_budget_stop_does_not_mark_failed_contests_complete(self):
        count=[0]
        api=fixture_api(10)
        def transport(url,timeout):
            count[0]+=1
            if count[0]==3: raise BudgetExceeded('pause now')
            return api(url,timeout)
        client=ApiClient(self.root,self.config,transport=transport,sleep=lambda _:None,compress_raw=True,data_kind='synthetic')
        with self.assertRaisesRegex(BudgetExceeded,'pause now'):
            collect(self.root,self.config,client=client)
        snap=read_json(self.root/'snapshot.json')
        self.assertTrue(snap['contests'])
        self.assertFalse(any(e['complete'] for e in snap['contests'].values()))
        offline=ApiClient(self.root,self.config,offline=True,data_kind='synthetic')
        replay=replay_scale(self.root,self.config,offline)
        self.assertEqual(replay['snapshot_sha256'],digest(snap))
        self.assertEqual(replay['network_requests_this_run'],0)
        resumed=collect(self.root,self.config,client=ApiClient(self.root,self.config,transport=api,
            sleep=lambda _:None,compress_raw=True,data_kind='synthetic'))
        self.assertEqual(resumed['complete'],10)
        self.assertNotIn('stop_reason',read_json(self.root/'snapshot.json')['coverage'])

    def test_budget_refuses_low_disk_before_attempting_network(self):
        from unittest.mock import patch
        from types import SimpleNamespace
        called=[]
        transport=BoundedTransport(self.root,self.limits,opener=lambda *_:called.append(1))
        with patch('lorem_study.scale_collect.shutil.disk_usage',return_value=SimpleNamespace(free=0)):
            with self.assertRaisesRegex(BudgetExceeded,'free disk'):
                transport('https://example.invalid',1)
        self.assertEqual(called,[])
        self.assertEqual(transport.state['attempts'],0)

    def test_offline_replay_rejects_a_truncated_terminal_page(self):
        self.make_fixture()
        prior=read_json(self.root/'snapshot.json')
        replay=replay_scale(self.root,self.config,ApiClient(self.root,self.config,offline=True,data_kind='synthetic'))
        self.assertEqual(replay['snapshot_sha256'],digest(prior))
        entry=next(iter(prior['contests'].values()))
        ref=entry['status_refs'][-1]
        ref['sha256']=put_bytes(self.root,canonical({'status':'OK','result':[{}]*self.config['page_size']}),compress=True)
        write_json(self.root/'snapshot.json',prior)
        with self.assertRaisesRegex(StudyError,'pagination is not complete'):
            replay_scale(self.root,self.config,ApiClient(self.root,self.config,offline=True,data_kind='synthetic'))

    def make_fixture(self):
        client=ApiClient(self.root,self.config,transport=fixture_api(10),sleep=lambda _:None,
                         compress_raw=True,data_kind='synthetic')
        collect(self.root,self.config,client=client)

    def test_synthetic_cache_cannot_be_imported_into_real_acquisition(self):
        self.make_fixture()
        destination=self.root/'real-target'
        with self.assertRaisesRegex(StudyError,'Only a real snapshot'):
            import_cache(destination,self.root)
        self.assertFalse((destination/'cache.json').exists())

    def test_partitioned_join_equals_original_and_resume(self):
        self.make_fixture()
        original=prepare(self.root)
        output=self.root/'analysis'
        scaled=prepare_scale(self.root,output,isolated=False,min_free_bytes=0)
        self.assertEqual([scaled[k] for k in ['observed_attempts','valid_events','valid_censored','excluded']],
                         [original[k] for k in ['observed_attempts','valid_events','valid_censored','excluded']])
        db=connect(output/'attempts.sqlite')
        self.addCleanup(db.close)
        expected={r['sample_id']:r for r in read_rows(self.root/'prepared'/'labels.jsonl')}
        actual=list(db.execute('SELECT sample_id,duration,event,quality,reason FROM attempts'))
        self.assertEqual(len(actual),len(expected))
        for sid,duration,event,quality,reason in actual:
            self.assertEqual((duration,event,quality,reason),tuple(expected[sid][k] for k in ['duration_seconds','event','quality','reason']))
        before=list(db.execute('SELECT * FROM attempts ORDER BY sample_id'))
        prepare_scale(self.root,output,isolated=False,min_free_bytes=0)
        self.assertEqual(before,list(db.execute('SELECT * FROM attempts ORDER BY sample_id')))
        part=output/'partitions'/str(scaled['prepared_contests'][0])
        (part/'prepared'/'audit.jsonl.gz').write_bytes(b'corrupt')
        with self.assertRaisesRegex(StudyError,'checksum'):
            prepare_scale(self.root,output,isolated=False,min_free_bytes=0)

    def test_join_mismatch_rolls_back_whole_partition(self):
        self.make_fixture()
        output=self.root/'analysis'
        report=prepare_scale(self.root,output,isolated=False,min_free_bytes=0)
        cid=report['prepared_contests'][0]
        part=output/'partitions'/str(cid)
        record=read_json(part/'partition.json')
        path=part/'prepared'/'audit.jsonl.gz'
        rows=gzip.decompress(path.read_bytes()).splitlines()
        import json,hashlib
        row=json.loads(rows[0]); row['sample_id']='different'; rows[0]=canonical(row)
        data=b'\n'.join(rows)+b'\n'; path.write_bytes(gzip.compress(data))
        record['streams']['audit'].update(sha256=file_sha(path),uncompressed_sha256=hashlib.sha256(data).hexdigest())
        write_json(part/'partition.json',record)
        db=connect(self.root/'empty.sqlite'); self.addCleanup(db.close)
        with self.assertRaisesRegex(StudyError,'1:1'):
            ingest_partition(db,part,record['partition_snapshot_sha256'])
        self.assertEqual(db.execute('SELECT COUNT(*) FROM attempts').fetchone()[0],0)
        self.assertEqual(db.execute('SELECT COUNT(*) FROM partitions').fetchone()[0],0)

    def test_valid_row_with_null_time_is_rejected_before_commit(self):
        self.make_fixture()
        output=self.root/'analysis'
        report=prepare_scale(self.root,output,isolated=False,min_free_bytes=0)
        part=output/'partitions'/str(report['prepared_contests'][0])
        record=read_json(part/'partition.json')
        import json,hashlib
        for name in ('labels','audit'):
            path=part/'prepared'/f'{name}.jsonl.gz'
            rows=[json.loads(line) for line in gzip.decompress(path.read_bytes()).splitlines()]
            row=next(r for r in rows if r['quality']=='valid')
            row['duration_seconds']=None
            data=b''.join(canonical(r)+b'\n' for r in rows)
            path.write_bytes(gzip.compress(data))
            record['streams'][name].update(sha256=file_sha(path),uncompressed_sha256=hashlib.sha256(data).hexdigest())
        write_json(part/'partition.json',record)
        db=connect(self.root/'null.sqlite');self.addCleanup(db.close)
        with self.assertRaisesRegex(StudyError,'finite positive'):
            ingest_partition(db,part,record['partition_snapshot_sha256'])
        self.assertEqual(db.execute('SELECT COUNT(*) FROM attempts').fetchone()[0],0)

    def test_original_array_hash_and_frozen_selection_are_verified(self):
        self.make_fixture()
        output=self.root/'analysis'
        report=prepare_scale(self.root,output,isolated=False,min_free_bytes=0)
        cid=report['prepared_contests'][0]
        part=output/'partitions'/str(cid)
        record=read_json(part/'partition.json')
        record['prepared']['labels_sha256']='0'*64
        write_json(part/'partition.json',record)
        db=connect(self.root/'checksum.sqlite');self.addCleanup(db.close)
        with self.assertRaisesRegex(StudyError,'canonical array checksum'):
            ingest_partition(db,part,record['partition_snapshot_sha256'])
        self.assertEqual(db.execute('SELECT COUNT(*) FROM attempts').fetchone()[0],0)
        before=file_sha(output/'attempts.sqlite')
        snapshot=read_json(self.root/'snapshot.json')
        snapshot['selection']['contest_ids'].reverse()
        write_json(self.root/'snapshot.json',snapshot)
        with self.assertRaisesRegex(StudyError,'selection/catalog changed'):
            prepare_scale(self.root,output,isolated=False,min_free_bytes=0)
        self.assertEqual(before,file_sha(output/'attempts.sqlite'))

    def test_shared_origins_external_transitive_bridges(self):
        groups=shared_groups([1,2,3,4],[{'contest_ids':[1,99]},{'contest_ids':[99,2]},
                                         {'contest_ids':[2,3]}])
        self.assertEqual(groups,[[1,2,3],[4]])

    def test_grouping_includes_shared_problems_with_no_attempts(self):
        snapshot={'contests':{}}
        for cid in [1,2]:
            result={'problems':[{'contestId':99,'index':'A'}],'rows':[]}
            ref={'sha256':put_bytes(self.root,canonical({'status':'OK','result':result}),compress=True)}
            snapshot['contests'][str(cid)]={'complete':True,'standings_refs':[ref]}
        links=structure_origin_links(self.root,snapshot)
        self.assertEqual(shared_groups([1,2],links),[[1,2]])
        self.assertEqual(links[0]['problem_keys'],[[99,'A']])

    def test_full_backup_restores_audit_http_hashes_and_offline_snapshot(self):
        from lorem_study.scale_backup import create_backup,restore_backup
        self.make_fixture()
        other=tempfile.TemporaryDirectory();self.addCleanup(other.cleanup)
        work=Path(other.name)
        output=work/'analysis'
        prepared=prepare_scale(self.root,output,isolated=False,min_free_bytes=0)
        models=work/'models';models.mkdir();write_json(models/'example.json',{'data_kind':'synthetic'})
        archive=work/'snapshot.tar.gz'
        result=create_backup(self.root,output,models,archive)
        self.assertGreater(result['raw_verification']['objects'],0)
        with self.assertRaisesRegex(StudyError,'archive checksum'):
            restore_backup(archive,work/'bad','0'*64)
        restored=restore_backup(archive,work/'restore',result['sha256'])
        self.assertEqual(restored['sqlite_integrity'],'ok')
        self.assertEqual(restored['raw'],result['raw_verification'])
        root=work/'restore/snapshot'
        prior=digest(read_json(root/'snapshot.json'))
        client=ApiClient(root,self.config,offline=True,data_kind='synthetic')
        replay=collect(root,self.config,offline=True,client=client)
        self.assertEqual(replay['snapshot_sha256'],prior)
        self.assertEqual(replay['network_requests_this_run'],0)
        # Raw hardlinks omitted from the archive are recreated from restored objects.
        new=prepare_scale(root,work/'restore/analysis',isolated=False,min_free_bytes=0)
        self.assertEqual(new['observed_attempts'],prepared['observed_attempts'])

    def test_restore_rejects_unsafe_archive_member(self):
        from lorem_study.scale_backup import restore_backup
        import tarfile,hashlib,io
        path=self.root/'bad.tar.gz'
        with tarfile.open(path,'w:gz') as target:
            info=tarfile.TarInfo('../escape');info.size=1
            target.addfile(info,io.BytesIO(b'x'))
        with self.assertRaisesRegex(StudyError,'Unsafe'):
            restore_backup(path,self.root/'restored',hashlib.sha256(path.read_bytes()).hexdigest())

    def test_coverage_reconciles_groups_contests_grid_and_global_distincts(self):
        from lorem_study.scale_delivery import coverage_report
        self.make_fixture()
        snap=read_json(self.root/'snapshot.json')
        snap['selection']['shortfall']=10
        write_json(self.root/'snapshot.json',snap)
        write_json(self.root/'universe.json',{'metadata_eligible':10,'regular':10,'upper_bound_before_standings':10})
        write_json(self.root/'screening.json',{'decisions':[{'eligible_structure':True,'reason':None} for _ in range(10)]})
        write_json(self.root/'network-budget.json',{'attempts':42,'http_bytes':1000,
            'first_start_utc':'2026-10-20T10:00:00Z','last_response_utc':'2026-10-20T10:02:00Z',
            'minimum_start_gap_seconds':2.0})
        other=tempfile.TemporaryDirectory();self.addCleanup(other.cleanup)
        output=Path(other.name)/'analysis'
        prepared=prepare_scale(self.root,output,isolated=False,min_free_bytes=0)
        analysis={'data_kind':'synthetic','snapshot_sha256':prepared['snapshot_sha256'],'plan_sha256':'test-plan',
                  'rows':[{'observed_attempts':90}],'outside_grid_observed':0,
                  'overlap_with_examined_models':{}}
        report=coverage_report(self.root,output,analysis)
        self.assertEqual(report['totals']['observed_attempts'],90)
        self.assertEqual(report['totals']['valid_events'],40)
        self.assertEqual(sum(g['excluded'] for g in report['by_group']),40)
        self.assertEqual(sum(c['valid_censored'] for c in report['by_contest']),10)
        # Every fixture contest repeats the same participant handles: global distincts
        # must be smaller than the sum of per-group distincts.
        self.assertLess(report['totals']['participants_valid_unique'],sum(g['participants_valid_unique'] for g in report['by_group']))
        analysis['outside_grid_observed']=1
        with self.assertRaisesRegex(StudyError,'grid coverage'):
            coverage_report(self.root,output,analysis)

    def test_shortfall_selection_is_frozen_before_status_or_ratings(self):
        self.config.update(target_contests=400)
        universe={'config_sha256':digest(self.config),'candidate_ids':[1,2,3],
                  'regular_ids':[1,2,3],'proven_versioned_ids':[2]}
        meta=[{'id':i,'type':'CF','phase':'FINISHED','startTimeSeconds':1600000000,'durationSeconds':7200} for i in [1,2,3]]
        body=canonical({'status':'OK','result':meta})
        universe['list_ref']={'sha256':put_bytes(self.root,body)}
        calls=[]
        class Client:
            def request(_,method,params):
                calls.append((method,params))
                cid=params['contestId']
                return {'contest':meta[cid-1],'rows':[],
                        'problems':[{'index':'A'},{'index':'B' if cid==1 else 'B1'}]}, {'sha256':'test'}
        selected=freeze_scale_selection(self.root,Client(),self.config,universe)
        self.assertEqual(selected['contest_ids'],[1])
        self.assertEqual(selected['shortfall'],399)
        self.assertEqual([c[0] for c in calls],['contest.standings']*2)
        freeze_scale_selection(self.root,Client(),self.config,universe)
        self.assertEqual(len(calls),2)


if __name__=='__main__': unittest.main()
