"""Canonical, finite JSON and content hashes shared by all stages."""

import hashlib
import gzip
import json
import os
from pathlib import Path
import re
import zlib


class StudyError(ValueError):
    pass


def canonical(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False, allow_nan=False,
                      separators=(",", ":")).encode("utf-8")


def digest(value):
    return hashlib.sha256(canonical(value)).hexdigest()


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def write_json(path, value):
    """Atomic replacement of pointers/tables; immutable raw objects use put_bytes."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    data = canonical(value) + b"\n"
    temp = path.with_name(path.name + f".{os.getpid()}.tmp")
    try:
        temp.write_bytes(data)
        temp.replace(path)
    finally:
        temp.unlink(missing_ok=True)


def put_bytes(root, data, *, compress=False):
    sha = hashlib.sha256(data).hexdigest()
    path = Path(root) / "raw" / f"{sha}.json{'.gz' if compress else ''}"
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        try:
            existing=gzip.decompress(path.read_bytes()) if compress else path.read_bytes()
        except (OSError,EOFError,zlib.error) as error:
            raise StudyError("Raw object corruption") from error
        if existing != data:
            raise StudyError("Raw object hash collision/corruption")
    else:
        temp = path.with_name(path.name + f".{os.getpid()}.tmp")
        try:
            temp.write_bytes(gzip.compress(data, compresslevel=5, mtime=0) if compress else data)
            temp.replace(path)
        finally:
            temp.unlink(missing_ok=True)
    return sha


def raw_json(root, ref):
    if not isinstance(ref.get("sha256"),str) or not re.fullmatch("[0-9a-f]{64}",ref["sha256"]):
        raise StudyError("Invalid raw SHA-256 identity")
    path = Path(root) / "raw" / f"{ref['sha256']}.json"
    if path.exists():
        data = path.read_bytes()
    else:
        try:
            data = gzip.decompress(path.with_suffix(".json.gz").read_bytes())
        except (OSError, EOFError, zlib.error) as error:
            raise StudyError("Raw compressed object missing/corrupt") from error
    if hashlib.sha256(data).hexdigest() != ref["sha256"]:
        raise StudyError("Raw checksum mismatch")
    envelope = json.loads(data)
    if not isinstance(envelope,dict) or envelope.get("status") != "OK" or "result" not in envelope:
        raise StudyError("Snapshot references a non-OK API response")
    return envelope["result"]


def write_rows(path, rows):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_name(path.name + ".tmp")
    with temp.open("wb") as output:
        for row in rows:
            output.write(canonical(row) + b"\n")
    temp.replace(path)


def read_rows(path):
    with Path(path).open(encoding="utf-8") as source:
        for line in source:
            if line.strip():
                yield json.loads(line)


def check_version(value):
    if value.get("schema_version") != 1:
        raise StudyError("Unsupported schema_version")
