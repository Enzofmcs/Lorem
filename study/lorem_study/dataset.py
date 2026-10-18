"""Verified joins and grouped partitions shared by model training."""

from .io import StudyError, check_version, digest, read_json, read_rows


def load_dataset(root):
    prepared = read_json(root / "prepared" / "manifest.json")
    splits = read_json(root / "splits.json")
    check_version(prepared)
    check_version(splits)
    if splits["prepared_sha256"] != digest(prepared):
        raise StudyError("Prepared dataset changed after split freeze")
    features = list(read_rows(root / "prepared" / "features.jsonl"))
    labels = list(read_rows(root / "prepared" / "labels.jsonl"))
    if digest(features) != prepared["features_sha256"] or digest(labels) != prepared["labels_sha256"]:
        raise StudyError("Prepared table checksum mismatch")
    partitions = splits["partitions"]
    if set(partitions) != {"train", "validation", "test"}:
        raise StudyError("Missing model partition")
    flat = [i for ids in partitions.values() for i in ids]
    if len(flat) != len(set(flat)) or set(flat) != set(prepared["complete_contest_ids"]):
        raise StudyError("Leaking/missing contest partition")
    if any(len(partitions[p]) < n for p, n in (("train", 6), ("validation", 2), ("test", 2))):
        raise StudyError("Model requires at least 6/2/2 complete contests")
    grouped = [cid for group in splits["groups"] for cid in group]
    if len(grouped) != len(set(grouped)) or set(grouped) != set(flat):
        raise StudyError("Missing/duplicate contest groups")
    for group in splits["groups"]:
        if sum(bool(set(group) & set(ids)) for ids in partitions.values()) != 1:
            raise StudyError("Shared group leaks between partitions")
    by_id = {f["sample_id"]: f for f in features}
    if len(by_id) != len(features) or len({r["sample_id"] for r in labels}) != len(labels):
        raise StudyError("Duplicate sample identities")
    if set(by_id) != {r["sample_id"] for r in labels}:
        raise StudyError("Feature/label identities mismatch")
    part_for = {cid: p for p, ids in partitions.items() for cid in ids}
    rows = {p: [] for p in partitions}
    for label in labels:
        f = by_id[label["sample_id"]]
        if f["contest_id"] != label["contest_id"]:
            raise StudyError("Feature/label contest mismatch")
        part = part_for.get(label["contest_id"])
        if part and label["quality"] == "valid":
            if (any(type(f.get(k)) is not int for k in ("old_rating", "problem_rating", "delta")) or
                f["old_rating"] <= 0 or f["problem_rating"] <= 0 or
                f["delta"] != f["problem_rating"] - f["old_rating"]):
                raise StudyError("Invalid model feature/label join")
            rows[part].append((f, label))
    return prepared, splits, features, rows
