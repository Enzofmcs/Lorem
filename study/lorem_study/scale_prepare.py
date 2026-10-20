"""Partitioned sequential-v1 preparation and a transactional on-disk analytic join.

The established preparer runs unchanged on ONE contest in each fresh process.
Only the three checksum-verified streams from that partition enter SQLite.
"""

import argparse
from collections import Counter, defaultdict
import gzip
import hashlib
from itertools import zip_longest
import json
import math
import os
from pathlib import Path
import re
import shutil
import sqlite3
import subprocess
import sys
import time

from .io import StudyError, digest, raw_json, read_json, write_json
from .prepare import prepare

TABLE = """CREATE TABLE IF NOT EXISTS attempts (
 sample_id TEXT PRIMARY KEY NOT NULL, contest_id INTEGER NOT NULL, participant TEXT NOT NULL,
 problem_contest_id INTEGER, problem_index TEXT, old_rating INTEGER, problem_rating INTEGER,
 delta INTEGER, quality TEXT NOT NULL, reason TEXT, duration REAL, event INTEGER,
 last_submission REAL, from_start REAL, group_id INTEGER,
 CHECK(quality != 'valid' OR (duration IS NOT NULL AND duration>0 AND event IS NOT NULL AND event IN (0,1))),
 CHECK(quality IN ('valid','excluded')))
"""


def file_sha(path):
    sha = hashlib.sha256()
    with Path(path).open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            sha.update(chunk)
    return sha.hexdigest()


def connect(path):
    db = sqlite3.connect(path)
    db.execute("PRAGMA cache_size=-65536")
    db.execute("PRAGMA temp_store=FILE")
    db.execute("PRAGMA foreign_keys=ON")
    db.execute(TABLE)
    db.execute("CREATE TABLE IF NOT EXISTS partitions (contest_id INTEGER PRIMARY KEY, source_sha TEXT, manifest_sha TEXT)")
    db.execute("CREATE TABLE IF NOT EXISTS metadata (name TEXT PRIMARY KEY, value TEXT)")
    return db


def _partition_worker(path):
    path = Path(path)
    started = time.monotonic()
    report = prepare(path)
    streams = {}
    for name in ("features", "labels", "audit"):
        plain = path / "prepared" / f"{name}.jsonl"
        output = plain.with_suffix(".jsonl.gz")
        temp = output.with_suffix(".tmp")
        with plain.open("rb") as source, temp.open("wb") as target:
            with gzip.GzipFile(fileobj=target, mode="wb", compresslevel=5, mtime=0) as compressed:
                shutil.copyfileobj(source, compressed, 1024 * 1024)
        temp.replace(output)
        streams[name] = {"path": output.name, "sha256": file_sha(output),
                         "uncompressed_sha256": file_sha(plain), "bytes": output.stat().st_size}
        plain.unlink()
    try:
        import resource
        peak = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss * (1 if sys.platform == "darwin" else 1024)
    except ImportError:
        peak = None  # Windows: measurement unavailable, never substitute zero.
    record = {"schema_version": 1, "partition_snapshot_sha256": digest(read_json(path / "snapshot.json")),
              "prepared": report, "streams": streams, "seconds": time.monotonic() - started,
              "peak_rss_bytes": peak}
    write_json(path / "partition.json", record)
    return record


def partition_snapshot(snapshot, cid):
    result = {k:v for k,v in snapshot.items() if k not in ("coverage", "contests")}
    result["contests"] = {str(cid): snapshot["contests"][str(cid)]}
    return result


def _rows(path, expected, array_hash=None):
    if file_sha(path) != expected["sha256"]:
        raise StudyError("Partition compressed stream checksum mismatch")
    sha = hashlib.sha256()
    first=True
    with gzip.open(path, "rb") as source:
        for line in source:
            sha.update(line)
            if line.strip():
                if array_hash is not None:
                    if not first:array_hash.update(b",")
                    array_hash.update(line.strip())
                    first=False
                yield json.loads(line)
    if sha.hexdigest() != expected["uncompressed_sha256"]:
        raise StudyError("Partition uncompressed stream checksum mismatch")
    if array_hash is not None:array_hash.update(b"]")


def ingest_partition(db, path, source_sha):
    """Rollback the whole contest on any mismatch, corruption, or duplicate sample."""
    path = Path(path)
    record = read_json(path / "partition.json")
    if record["partition_snapshot_sha256"] != source_sha:
        raise StudyError("Partition source snapshot mismatch")
    prior = db.execute("SELECT source_sha,manifest_sha FROM partitions WHERE contest_id=?",
                       (record["prepared"]["contests"][0]["contest_id"],)).fetchone()
    if prior:
        if prior != (source_sha, digest(record)):
            raise StudyError("An ingested partition changed; rebuild in a new analysis directory")
        # Do not skip integrity verification just because the DB contains this partition.
        for stream in record["streams"].values():
            if file_sha(path / "prepared" / stream["path"]) != stream["sha256"]:
                raise StudyError("Cached partition checksum mismatch")
        return False
    counts = Counter()
    cid = record["prepared"]["contests"][0]["contest_id"]
    array_hashes={n:hashlib.sha256(b"[") for n in ("features","labels")}
    streams = [_rows(path / "prepared" / record["streams"][n]["path"], record["streams"][n],array_hashes.get(n))
               for n in ("features", "labels", "audit")]
    with db:
        for feature, label, audit in zip_longest(*streams):
            if (any(v is None for v in (feature, label, audit)) or
                    len({v["sample_id"] for v in (feature, label, audit)}) != 1 or
                    any(v["contest_id"] != cid for v in (feature, label, audit)) or
                    feature["participant_key"] != audit["participant_key"] or
                    label["quality"] != audit["quality"] or label["reason"] != audit["reason"]):
                raise StudyError("Features/labels/audit must form an exact 1:1 aligned join")
            valid = label["quality"] == "valid"
            if any(not isinstance(v,str) or not re.fullmatch("[0-9a-f]{64}",v)
                   for v in (feature["sample_id"],feature["participant_key"])):
                raise StudyError("Analytic sample/participant identities must be SHA-256 pseudonyms")
            if valid and (type(label["duration_seconds"]) not in (int,float) or
                          not math.isfinite(label["duration_seconds"]) or label["duration_seconds"]<=0 or
                          type(label["event"]) is not int or label["event"] not in (0,1)):
                raise StudyError("Valid attempts require finite positive durations and event 0/1")
            if valid and (label["duration_seconds"] != audit["duration_seconds"] or
                          label["event"] != audit["event"]):
                raise StudyError("Label/audit event or duration mismatch")
            from_start = (audit["observed_end_seconds"] - audit["start_seconds"]) if valid else None
            values = (feature["sample_id"], cid, feature["participant_key"], feature["problem_contest_id"],
                      feature["problem_index"], feature["old_rating"], feature["problem_rating"], feature["delta"],
                      label["quality"], label["reason"], label["duration_seconds"], label["event"],
                      label["sensitivity_last_submission_seconds"], from_start, None)
            try:
                db.execute("INSERT INTO attempts VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", values)
            except sqlite3.IntegrityError as error:
                raise StudyError("Duplicate/invalid analytic sample; partition rolled back") from error
            counts["observed_attempts"] += 1
            counts["valid_events" if valid and label["event"] else "valid_censored" if valid else "excluded"] += 1
        for name,sha in array_hashes.items():
            if sha.hexdigest()!=record["prepared"][f"{name}_sha256"]:
                raise StudyError("Stream rows differ from the core preparer canonical array checksum")
        for name in ("observed_attempts", "valid_events", "valid_censored", "excluded"):
            if counts[name] != record["prepared"][name]:
                raise StudyError("Partition funnel does not reconcile")
        db.execute("INSERT INTO partitions VALUES (?,?,?)", (cid, source_sha, digest(record)))
    return True


def shared_groups(ids, links):
    """External origin nodes can bridge two selected contests transitively."""
    parent = {i:i for i in ids}

    def find(i):
        parent.setdefault(i,i)
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for link in links:
        nodes = link["contest_ids"]
        for i in nodes:
            find(i)
        for i in nodes[1:]:
            parent[find(i)] = find(nodes[0])
    groups = defaultdict(list)
    for cid in ids:
        groups[find(cid)].append(cid)
    return sorted([sorted(values) for values in groups.values()])


def structure_origin_links(root,snapshot):
    """Use every verified problem identity, including problems never attempted.

    This is available from the frozen structural screening and never chooses
    bootstrap links based on ACs, censored times or the existence of attempts.
    """
    links=[]
    for key,entry in sorted(snapshot["contests"].items(),key=lambda x:int(x[0])):
        if not entry.get("complete"):continue
        cid=int(key)
        for ref in entry.get("standings_refs",[]):
            result=raw_json(root,ref)
            keys=sorted({(p["contestId"],p["index"]) for p in result["problems"]
                         if type(p.get("contestId")) is int and p["contestId"]>0 and isinstance(p.get("index"),str)})
            links.append({"contest_ids":sorted({cid}|{origin for origin,_ in keys}),
                          "problem_keys":[[origin,index] for origin,index in keys],"standings_ref":ref,
                          "source":"full frozen standings identities, including unattempted problems"})
    return links


def prepare_scale(root, output, *, isolated=True, min_free_bytes=4 * 1024**3):
    root, output = Path(root).resolve(), Path(output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    snapshot = read_json(root / "snapshot.json")
    started = time.monotonic()
    db = connect(output / "attempts.sqlite")
    config_sha = digest(snapshot["config"])
    identities={"config_sha256":config_sha,"selection_sha256":digest(snapshot["selection"]),
                "problem_catalog_ref_sha256":digest(snapshot["problems_ref"])}
    for name,value in identities.items():
        prior=db.execute("SELECT value FROM metadata WHERE name=?",(name,)).fetchone()
        if prior and prior[0]!=value:
            db.close()
            raise StudyError("Analysis config/selection/catalog changed; use a new directory")
    # Support checkpoints from the initial implementation without accepting a
    # differently selected cohort into the same transactional database.
    for (cid,) in db.execute("SELECT contest_id FROM partitions"):
        old=read_json(output/"partitions"/str(cid)/"snapshot.json")
        if old["selection"]!=snapshot["selection"] or old["problems_ref"]!=snapshot["problems_ref"]:
            db.close()
            raise StudyError("Partition belongs to another selection/catalog")
    with db:
        db.executemany("INSERT OR IGNORE INTO metadata VALUES (?,?)",identities.items())
    records, skipped = [], []
    try:
        for cid in snapshot["selection"]["contest_ids"]:
            entry = snapshot["contests"].get(str(cid), {})
            if not entry.get("complete"):
                skipped.append({"contest_id":cid,"reason":entry.get("error", "collection_incomplete")})
                continue
            if shutil.disk_usage(output).free < min_free_bytes:
                raise StudyError("Preparation paused: free disk reserve reached")
            part = output / "partitions" / str(cid)
            part.mkdir(parents=True, exist_ok=True)
            # Hardlink only referenced immutable objects, avoiding symlink privileges
            # on Windows. A copy fallback is bounded by the same free-disk checks.
            raw_dir = part / "raw"
            raw_dir.mkdir(exist_ok=True)
            refs = [snapshot["selection"]["list_ref"], snapshot["problems_ref"],
                    entry["rating_changes_ref"], *entry["standings_refs"], *entry["status_refs"]]
            for ref in refs:
                plain = root / "raw" / f"{ref['sha256']}.json"
                source = plain if plain.exists() else plain.with_suffix(".json.gz")
                target = raw_dir / source.name
                if not target.exists():
                    try:
                        os.link(source,target)
                    except OSError:
                        if shutil.disk_usage(output).free < min_free_bytes+source.stat().st_size:
                            raise StudyError("Preparation copy would consume disk reserve")
                        shutil.copyfile(source,target)
            small = partition_snapshot(snapshot, cid)
            source_sha = digest(small)
            if (part / "snapshot.json").exists() and read_json(part / "snapshot.json") != small:
                raise StudyError("Partition raw source changed; use another analysis directory")
            write_json(part / "snapshot.json", small)
            if not (part / "partition.json").exists():
                if isolated:
                    result = subprocess.run([sys.executable, "-m", "lorem_study.scale_prepare", "--worker", str(part)],
                                            capture_output=True, text=True)
                    if result.returncode:
                        raise StudyError(f"Preparation worker {cid} failed: {result.stderr[-2000:]}")
                else:
                    _partition_worker(part)
            record = read_json(part / "partition.json")
            inserted = ingest_partition(db, part, source_sha)
            records.append({"contest_id":cid, "source_sha256":source_sha,
                            "partition_manifest_sha256":digest(record), **record})
            if inserted:
                print(f"prepared contest={cid} rows={record['prepared']['observed_attempts']} rss={record['peak_rss_bytes']}", flush=True)
        ids = [r["contest_id"] for r in records]
        ingested = [r[0] for r in db.execute("SELECT contest_id FROM partitions")]
        if set(ids) != set(ingested):
            raise StudyError("Database contains contests absent from current complete snapshot")
        links = [link for r in records for link in r["prepared"]["origin_links"]]
        links.extend(structure_origin_links(root,snapshot))
        groups = shared_groups(ids, links)
        with db:
            for group in groups:
                for cid in group:
                    db.execute("UPDATE attempts SET group_id=? WHERE contest_id=?", (min(group),cid))
            for name, columns in (("cell", "quality,old_rating,problem_rating"),
                                  ("contest", "contest_id"), ("person", "participant")):
                db.execute(f"CREATE INDEX IF NOT EXISTS idx_{name} ON attempts ({columns})")
            db.execute("INSERT OR REPLACE INTO metadata VALUES ('snapshot_sha256',?)", (digest(snapshot),))
        total = db.execute("SELECT COUNT(*),SUM(quality='valid' AND event=1),SUM(quality='valid' AND event=0),SUM(quality='excluded'),COUNT(DISTINCT participant) FROM attempts").fetchone()
        if total[0] != sum(r["prepared"]["observed_attempts"] for r in records):
            raise StudyError("Global on-disk funnel mismatch")
        repeated = db.execute("SELECT COUNT(*) FROM (SELECT participant FROM attempts WHERE quality='valid' GROUP BY participant HAVING COUNT(DISTINCT group_id)>1)").fetchone()[0]
        report = {"schema_version":1,"version":"scale-t50-prepared-v1","data_kind":snapshot["data_kind"],
                  "snapshot_sha256":digest(snapshot),"selection_sha256":digest(snapshot["selection"]),
                  "config_sha256":config_sha,"observed_attempts":total[0],"valid_events":total[1] or 0,
                  "valid_censored":total[2] or 0,"excluded":total[3] or 0,"participants_all_unique":total[4],
                  "participants_repeated_across_groups":repeated,"prepared_contests":ids,
                  "eligible_contest_ids":sorted(cid for r in records for cid in r["prepared"]["complete_contest_ids"]),
                  "groups":groups,"origin_links":links,"skipped":skipped,"partitions":records,
                  "grouping_method":"transitive verified source contests; full standings include unattempted problems",
                  "preparation_seconds_this_run":time.monotonic()-started,
                  "maximum_worker_rss_bytes":max((r["peak_rss_bytes"] for r in records if r["peak_rss_bytes"] is not None),default=None),
                  "database_bytes":(output / "attempts.sqlite").stat().st_size,
                  "database_sha256":file_sha(output / "attempts.sqlite"),
                  "limitations":["Known shared origins only: aliases may be undetected", "Repeated participants can couple groups", "No new independent predictive evaluation"]}
        write_json(output / "manifest.json",report)
        return report
    finally:
        db.close()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--worker", type=Path)
    args = parser.parse_args(argv)
    if args.worker:
        _partition_worker(args.worker)
    elif args.root and args.output:
        from .cli import work_lock
        with work_lock(args.output):
            report = prepare_scale(args.root,args.output)
            print({k:report[k] for k in ("observed_attempts","valid_events","valid_censored","excluded","maximum_worker_rss_bytes")})
    else:
        parser.error("root and --output required")


if __name__ == "__main__":
    main()
