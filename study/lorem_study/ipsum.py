"""Local, pseudonymous Ipsum cohort preparation; no transport model or integration."""

from collections import Counter
from pathlib import Path
import re
import uuid

from .api import ApiClient
from .config import load_config
from .io import StudyError, digest, read_json, write_json, write_rows
from .selection import regular_round, simple_order

PROTOCOL = {"schema_version": 1, "protocol_version": "ipsum-participant-v1", "seed": 20261018,
            "export_version": "ipsum-local-v1", "recording_version": "ipsum-study-v1",
            "cutoff_epoch_ms": 1593561600000, "minimum_participants": 10, "minimum_attempts": 200,
            "minimum_events": 100, "partition_method": "whole participants; seeded SHA256 order; 60/20/20, minimum 6/2/2",
            "eligibility": "verified origin/date; regular simple whole contest, no easy/hard versions; prior CF snapshot; positive wall time",
            "hint_strata": ["without_hint", "with_hint", "unknown"],
            "interruption_policy": "unknown remains unknown; wall clock is not dedication",
            "gate": "preparation floor only; Story 31 must preregister final precision/acceptance before test; 32 remains blocked"}


def _integer(value, *, positive=True):
    return type(value) is int and value >= (1 if positive else 0)


def validate_export(value):
    if value.get("schema_version") != 1 or value.get("export_version") != PROTOCOL["export_version"]:
        raise StudyError("Unsupported Ipsum export version")
    if value.get("data_kind") not in ("ipsum_local", "synthetic"):
        raise StudyError("Ipsum exports must distinguish local observations and synthetic fixtures")
    try:
        participant = str(uuid.UUID(value["participant_key"]))
    except (ValueError, KeyError, TypeError, AttributeError) as error:
        raise StudyError("Invalid pseudonymous participant key") from error
    if not isinstance(value.get("attempts"), list):
        raise StudyError("Missing Ipsum attempts")
    for row in value["attempts"]:
        if (not isinstance(row, dict) or not re.fullmatch(r"[0-9a-f]{64}", str(row.get("sample_id", ""))) or
            not _integer(row.get("problem_contest_id")) or not isinstance(row.get("problem_index"), str) or
            not row["problem_index"] or row.get("status") not in ("COMPLETED", "PENDING", "SOLVED_OUTSIDE")):
            raise StudyError("Invalid/active Ipsum export row; active attempts must remain private")
    return {**value, "participant_key": participant}


def quality(row, meta):
    reasons = []
    start, end = row.get("started_at_epoch_ms"), row.get("ended_at_epoch_ms")
    if not _integer(start) or not _integer(end) or end <= start:
        reasons.append("missing_or_nonpositive_window")
    cf, observed = row.get("cf_rating_before"), row.get("cf_rating_observed_at_epoch_ms")
    if not _integer(cf) or not _integer(observed) or row.get("cf_rating_source") != "codeforces:user.info":
        reasons.append("missing_prior_cf_snapshot")
    elif not _integer(start) or observed > start:
        reasons.append("cf_snapshot_after_start")
    if not _integer(row.get("lorem_rating_before"), positive=False) or not _integer(row.get("problem_rating")):
        reasons.append("missing_rating_snapshot")
    if row.get("recording_version") != PROTOCOL["recording_version"]:
        reasons.append("legacy_recording_fields_unknown")
    if row.get("status") == "COMPLETED":
        if row.get("first_ac_at_epoch_ms") != end or not _integer(end):
            reasons.append("invalid_first_ac")
    elif row.get("first_ac_at_epoch_ms") is not None:
        reasons.append("upsolve_is_not_ipsum_event")
    hint = row.get("hint_revealed_at_epoch_ms")
    if hint is not None and (not _integer(hint) or not _integer(start) or not _integer(end) or not start <= hint <= end):
        reasons.append("hint_time_outside_window")
    if type(row.get("hint_used")) is not bool or row["hint_used"] != (hint is not None):
        reasons.append("missing_or_conflicting_hint_status")
    if row.get("wall_clock_only") is not True:
        reasons.append("unverified_time_definition")
    upsolve = row.get("upsolve_ac_at_epoch_ms")
    if upsolve is not None and (not _integer(upsolve) or not _integer(end) or upsolve <= end or row["status"] != "SOLVED_OUTSIDE"):
        reasons.append("invalid_upsolve_window")
    origin = row.get("origin_started_at_epoch_ms")
    if (not _integer(origin) or origin < PROTOCOL["cutoff_epoch_ms"] or
        row.get("eligibility_version") != "origin-20200701-v1" or
        row.get("origin_source") != "codeforces:contest.list" or not _integer(row.get("origin_verified_at_epoch_ms"))):
        reasons.append("missing_or_ineligible_origin_snapshot")
    contest, problems = meta.get("contest", {}), meta.get("problems", [])
    if (contest.get("id") != row["problem_contest_id"] or not regular_round(contest) or
        contest.get("phase") != "FINISHED"):
        reasons.append("special_or_unverified_contest")
    if not simple_order(problems):
        reasons.append("whole_contest_has_unsupported_order")
    seconds = contest.get("startTimeSeconds")
    if type(seconds) is not int or seconds * 1000 != origin:
        reasons.append("origin_date_conflict")
    if _integer(start) and _integer(origin) and start < origin:
        reasons.append("attempt_before_origin")
    if not any(p.get("index") == row["problem_index"] and p.get("contestId") == row["problem_contest_id"] for p in problems):
        reasons.append("unverified_problem_origin")
    return reasons


def prepare_ipsums(root, paths=(), *, offline=False, metadata_fetch=None):
    root = Path(root) / "ipsum"
    plan = root / "plan.json"
    if plan.exists() and read_json(plan) != PROTOCOL:
        raise StudyError("Ipsum preparation protocol changed; use a new work directory")
    write_json(plan, PROTOCOL)
    exports = [validate_export(read_json(path)) for path in paths]
    kinds = {v["data_kind"] for v in exports}
    if len(kinds) > 1:
        raise StudyError("Cannot mix synthetic and local Ipsum observations")
    kind = next(iter(kinds), "not_collected")
    records, seen = [], {}
    for value in exports:
        for row in value["attempts"]:
            key = (value["participant_key"], row["sample_id"])
            if key in seen:
                if seen[key] != row:
                    raise StudyError("Conflicting Ipsum exports; select one consistent snapshot per participant")
                continue
            seen[key] = row
            records.append({**row, "participant_key": key[0]})
    records.sort(key=lambda r: (r["participant_key"], r["sample_id"]))
    try:
        dataset_sha = digest(records)
    except ValueError as error:
        raise StudyError("Ipsum exports require finite JSON values") from error
    partition_path = root / "partitions.json"
    if partition_path.exists() and read_json(partition_path)["dataset_sha256"] != dataset_sha:
        raise StudyError("Ipsum cohort already frozen; use a new directory, no test retuning")
    if kind == "synthetic" and records and metadata_fetch is None:
        raise StudyError("Synthetic Ipsum fixtures require isolated synthetic metadata, never live API")
    client = ApiClient(root / "api", load_config(), offline=offline)
    fetch = metadata_fetch or (lambda cid: client.request("contest.standings", {"contestId": cid}))
    metadata, refs = {}, {}
    for cid in sorted({r["problem_contest_id"] for r in records}):
        data, ref = fetch(cid)
        if kind == "synthetic" and ref.get("data_kind") != "synthetic":
            raise StudyError("Synthetic Ipsum fixtures cannot use real metadata")
        if (not isinstance(data, dict) or not isinstance(data.get("problems"), list) or
            not all(isinstance(p, dict) for p in data["problems"]) or not isinstance(data.get("contest"), dict) or
            data["contest"].get("id") != cid):
            raise StudyError("Invalid full contest metadata; stop/resume rather than manufacture censoring")
        metadata[cid] = {"contest": data["contest"], "problems": data["problems"]}
        refs[str(cid)] = ref
    reasons = Counter()
    for row in records:
        row["quality_reasons"] = quality(row, metadata[row["problem_contest_id"]])
        row["quality"] = "valid" if not row["quality_reasons"] else "excluded"
        reasons.update(row["quality_reasons"])
        row["event"] = int(row["status"] == "COMPLETED") if not row["quality_reasons"] else None
        row["duration_seconds"] = ((row["ended_at_epoch_ms"]-row["started_at_epoch_ms"])/1000
                                   if not row["quality_reasons"] else None)
    valid = [r for r in records if r["quality"] == "valid"]
    participants = sorted({r["participant_key"] for r in valid}, key=lambda p: digest([PROTOCOL["seed"], p]))
    enough = (len(participants) >= PROTOCOL["minimum_participants"] and len(valid) >= PROTOCOL["minimum_attempts"] and
              sum(r["event"] for r in valid) >= PROTOCOL["minimum_events"])
    partitions = None
    if enough:
        n = len(participants); a, b = max(6, round(.6*n)), max(2, round(.2*n))
        partitions = {"schema_version": 1, "dataset_sha256": dataset_sha, "protocol_sha256": digest(PROTOCOL),
                      "participants": {"train": participants[:a], "validation": participants[a:a+b], "test": participants[a+b:]}}
        if partition_path.exists() and read_json(partition_path) != partitions:
            raise StudyError("Ipsum participant partition is frozen")
        write_json(partition_path, partitions)
    summary = {"schema_version": 1, "data_kind": kind, "protocol": PROTOCOL, "dataset_sha256": dataset_sha,
               "status": "prepared_for_history31; not_transport_evidence" if enough else "insufficient_data",
               "observed_attempts": len(records), "valid_attempts": len(valid), "valid_participants": len(participants),
               "events": sum(r["event"] for r in valid), "censored": sum(1-r["event"] for r in valid),
               "excluded": len(records)-len(valid), "reasons_multiple_per_row": dict(reasons),
               "hint_strata_valid": dict(Counter("with_hint" if r.get("hint_revealed_at_epoch_ms") is not None else "without_hint" for r in valid)),
               "interruptions_unknown_valid": sum(r.get("interruptions") is None for r in valid),
               "metadata_refs": refs, "network_requests": client.network_requests,
               "partitions_created": partitions is not None, "transport_evaluation": "NOT EXECUTED; History 31",
               "integration_gate": "CLOSED; retain D-012 and distinct ratings"}
    write_rows(root / "attempts.jsonl", records)
    write_json(root / "manifest.json", summary)
    return summary
