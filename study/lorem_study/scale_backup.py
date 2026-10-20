"""Verified full backups. Raw handles stay local; presentation bundles are separate."""

import argparse
import gzip
import hashlib
import io
from pathlib import Path
import sqlite3
import tarfile
import zlib

from .io import StudyError, canonical, digest, read_json
from .scale_prepare import file_sha


def verify_raw(root):
    count,total,stored=0,0,0
    for path in sorted((Path(root)/"raw").iterdir()):
        if not (path.name.endswith(".json") or path.name.endswith(".json.gz")):
            raise StudyError("Unexpected raw object; remove unfinished temporary files before backup")
        expected=path.name.split(".")[0]
        sha=hashlib.sha256()
        opener=gzip.open if path.suffix==".gz" else open
        try:
            with opener(path,"rb") as stream:
                for chunk in iter(lambda:stream.read(1024*1024),b""):
                    sha.update(chunk);total+=len(chunk)
        except (OSError,EOFError,zlib.error) as error:
            raise StudyError("Corrupt compressed raw object") from error
        if sha.hexdigest()!=expected:raise StudyError("Decompressed HTTP object checksum mismatch")
        count+=1;stored+=path.stat().st_size
    return {"objects":count,"http_bytes":total,"stored_bytes":stored}


def create_backup(snapshot,analysis,models,archive):
    roots={"snapshot":Path(snapshot).resolve(),"analysis":Path(analysis).resolve(),"models":Path(models).resolve()}
    archive=Path(archive).resolve()
    if archive.exists():raise StudyError("Do not overwrite an existing backup")
    if any(root==archive or root in archive.parents for root in roots.values()):
        raise StudyError("Backup must be outside input directories")
    snapshot_value=read_json(roots["snapshot"]/"snapshot.json")
    prepared=read_json(roots["analysis"]/"manifest.json")
    if prepared["snapshot_sha256"]!=digest(snapshot_value):raise StudyError("Backup snapshot/prepared mismatch")
    if prepared["database_sha256"]!=file_sha(roots["analysis"]/"attempts.sqlite"):
        raise StudyError("Backup analytic database checksum mismatch")
    raw_verification=verify_raw(roots["snapshot"])
    files={}
    sources={}
    for prefix,root in roots.items():
        for path in sorted(root.rglob("*")):
            relative=path.relative_to(root)
            if path.name==".lock" or path.name.endswith(".tmp"):
                continue
            if prefix=="analysis" and "partitions" in relative.parts and "raw" in relative.parts:
                continue  # derived hardlinks reconstructed on restore/preparation
            if path.is_symlink():raise StudyError("Backup inputs may not contain symbolic links")
            if path.is_file():
                name=f"{prefix}/{relative.as_posix()}"
                files[name]={"sha256":file_sha(path),"bytes":path.stat().st_size}
                sources[name]=path
    manifest={"schema_version":1,"version":"scale-t50-backup-v1","snapshot_sha256":digest(snapshot_value),
              "prepared_sha256":digest(prepared),"raw_verification":raw_verification,"files":files,
              "privacy":"Contains public handles/pseudonyms in raw/audit; local full backup, excluded from Git",
              "restore":"restore to new directory; analysis/partitions/*/raw hardlinks are rebuilt by scale_prepare"}
    archive.parent.mkdir(parents=True,exist_ok=True)
    temp=archive.with_suffix(archive.suffix+".tmp")
    try:
        with temp.open("wb") as stream,gzip.GzipFile(fileobj=stream,mode="wb",compresslevel=1,mtime=0) as compressed:
            with tarfile.open(fileobj=compressed,mode="w|",dereference=True) as target:
                body=canonical(manifest)+b"\n"
                info=tarfile.TarInfo("backup-manifest.json");info.size=len(body);info.mode=0o600
                target.addfile(info,io.BytesIO(body))
                for name,path in sources.items():
                    target.add(path,arcname=name,recursive=False)
        temp.replace(archive)
    finally:
        temp.unlink(missing_ok=True)
    return {"path":str(archive),"bytes":archive.stat().st_size,"sha256":file_sha(archive),
            "manifest_sha256":digest(manifest),"files":len(files),"raw_verification":raw_verification}


def restore_backup(archive,destination,expected_sha256):
    archive,destination=Path(archive),Path(destination).resolve()
    if destination.exists():raise StudyError("Restore destination must not exist")
    if file_sha(archive)!=expected_sha256:raise StudyError("Backup archive checksum mismatch")
    with tarfile.open(archive,"r:gz") as source:
        members=source.getmembers()
        seen=set()
        for member in members:
            path=(destination/member.name).resolve()
            if (destination not in path.parents or member.name in seen or not member.isfile()):
                raise StudyError("Unsafe or duplicate backup member")
            seen.add(member.name)
        destination.mkdir(parents=True)
        options={"filter":"data"} if hasattr(tarfile,"data_filter") else {}
        source.extractall(destination,members=members,**options)
    manifest=read_json(destination/"backup-manifest.json")
    if manifest.get("version")!="scale-t50-backup-v1":raise StudyError("Unsupported backup version")
    actual={p.relative_to(destination).as_posix() for p in destination.rglob("*") if p.is_file()}
    if actual!={"backup-manifest.json",*manifest["files"]}:raise StudyError("Backup file inventory mismatch")
    for name,value in manifest["files"].items():
        path=destination/name
        if path.stat().st_size!=value["bytes"] or file_sha(path)!=value["sha256"]:
            raise StudyError("Restored file checksum mismatch")
    if digest(read_json(destination/"snapshot/snapshot.json"))!=manifest["snapshot_sha256"]:
        raise StudyError("Restored snapshot mismatch")
    raw=verify_raw(destination/"snapshot")
    if raw!=manifest["raw_verification"]:raise StudyError("Restored HTTP object inventory mismatch")
    with sqlite3.connect((destination/'analysis/attempts.sqlite').as_uri()+"?mode=ro",uri=True) as db:
        if db.execute("PRAGMA integrity_check").fetchone()[0]!="ok":raise StudyError("Restored SQLite integrity check failed")
    return {"archive_sha256":expected_sha256,"manifest_sha256":digest(manifest),"destination":str(destination),
            "files_verified":len(manifest["files"]),"raw":raw,"sqlite_integrity":"ok"}


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    sub=parser.add_subparsers(dest="command",required=True)
    create=sub.add_parser("create")
    for name in ("snapshot","analysis","models","archive"):create.add_argument(name,type=Path)
    restore=sub.add_parser("restore")
    restore.add_argument("archive",type=Path);restore.add_argument("destination",type=Path);restore.add_argument("--sha256",required=True)
    args=parser.parse_args(argv)
    if args.command=="create":
        from .cli import work_lock
        with work_lock(args.snapshot),work_lock(args.analysis):
            result=create_backup(args.snapshot,args.analysis,args.models,args.archive)
    else:
        result=restore_backup(args.archive,args.destination,args.sha256)
    print(canonical(result).decode())


if __name__=="__main__":main()
