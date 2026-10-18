"""Canonical, finite JSON and content hashes shared by all stages."""

import hashlib
import json
import os
from pathlib import Path


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


def put_bytes(root, data):
    sha = hashlib.sha256(data).hexdigest()
    path = Path(root) / "raw" / f"{sha}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        if path.read_bytes() != data:
            raise StudyError("Raw object hash collision/corruption")
    else:
        with path.open("xb") as output:
            output.write(data)
    return sha


def raw_json(root, ref):
    data = (Path(root) / "raw" / f"{ref['sha256']}.json").read_bytes()
    if hashlib.sha256(data).hexdigest() != ref["sha256"]:
        raise StudyError("Raw checksum mismatch")
    envelope = json.loads(data)
    if envelope.get("status") != "OK":
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
