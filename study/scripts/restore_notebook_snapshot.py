"""Restore the exact historical backup in a NEW directory, with SHA-256 verification.

No network, training, or overwriting. Public handles stay in ignored local storage.
"""
import argparse
import hashlib
from pathlib import Path
import tarfile

EXPECTED = "86305d7f0c7152c80733b1019aae95480f03bea07b52742266b72ea4b94012c9"


def restore(archive, destination, expected_sha256=EXPECTED):
    archive, destination = Path(archive), Path(destination).resolve()
    if destination.exists():
        raise ValueError("Destination must not exist")
    sha = hashlib.sha256()
    with archive.open("rb") as stream:
        for block in iter(lambda: stream.read(1024*1024), b""):
            sha.update(block)
    if sha.hexdigest() != expected_sha256:
        raise ValueError("Historical archive checksum mismatch")
    with tarfile.open(archive, "r:gz") as source:
        members = source.getmembers()
        for member in members:
            path = (destination / member.name).resolve()
            if (path != destination and destination not in path.parents) or not (member.isfile() or member.isdir()):
                raise ValueError("Unsafe archive member")
        destination.mkdir(parents=True)
        options = {"filter": "data"} if hasattr(tarfile, "data_filter") else {}
        source.extractall(destination, members=members, **options)
    return {"archive_sha256": sha.hexdigest(), "destination": str(destination),
            "next": "Find snapshot.json and validate with full mode; old backups may need audit regenerated in a copy. See notebooks/README.md"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--sha256", default=EXPECTED, help="Expected archive hash; default is the original 18/10 backup")
    args = parser.parse_args()
    print(restore(args.archive, args.destination, args.sha256))
