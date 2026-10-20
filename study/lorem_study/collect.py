"""Freeze selection before modelling; only fully paginated contests are complete."""

from pathlib import Path
import re

from .api import ApiClient
from .config import cutoff_seconds
from .io import StudyError, digest, raw_json, read_json, write_json
from .selection import POLICY, screen_selection


def eligible_contest(contest, cutoff):
    return (type(contest.get("id")) is int and contest["id"] > 0 and
            contest.get("phase") == "FINISHED" and contest.get("type") == "CF" and
            type(contest.get("startTimeSeconds")) is int and
            contest["startTimeSeconds"] >= cutoff and
            type(contest.get("durationSeconds")) is int and contest["durationSeconds"] > 0)


def division(contest):
    matches = re.findall(r"Div\.?\s*(\d)", contest.get("name", ""), re.IGNORECASE)
    return "+".join(sorted(set(matches))) or "other"


def select_contests(contests, config, count):
    pools = {}
    for contest in contests:
        if eligible_contest(contest, cutoff_seconds(config)):
            pools.setdefault(division(contest), []).append(contest["id"])
    for pool in pools.values():
        pool.sort(key=lambda i: digest([config["seed"], i]))
    selected = []
    while len(selected) < count and any(pools.values()):
        for name in sorted(pools):
            if pools[name] and len(selected) < count:
                selected.append(pools[name].pop(0))
    return selected


def _pages(client, method, contest_id, config):
    if method == "contest.standings":
        # Non-gym standings now accept only anonymous GET with contestId.
        # Omitting from/count returns the full official standings, not a page.
        result, ref = client.request(method, {"contestId": contest_id})
        if (not isinstance(result, dict) or not isinstance(result.get("rows"), list) or
            not isinstance(result.get("contest"), dict) or
            result["contest"].get("id") != contest_id or
            not isinstance(result.get("problems"), list)):
            raise StudyError("Malformed full standings or contest identity/problems mismatch")
        return [ref]
    refs, seen_full = [], set()
    for page in range(config["max_pages"]):
        params = {"contestId": contest_id, "from": page * config["page_size"] + 1,
                  "count": config["page_size"]}
        result, ref = client.request(method, params)
        rows = result.get("rows") if isinstance(result, dict) else result
        if not isinstance(rows, list):
            raise StudyError(f"Malformed {method} page")
        if len(rows) > config["page_size"]:
            raise StudyError("API page exceeded requested count")
        refs.append(ref)
        if len(rows) < config["page_size"]:
            return refs
        page_hash = digest(rows)
        if page_hash in seen_full:
            raise StudyError("Repeated full page; pagination cannot be verified")
        seen_full.add(page_hash)
    raise StudyError("Pagination limit reached; contest remains incomplete")


def _pending(root, refs, meta):
    count = 0
    for ref in refs:
        for submission in raw_json(root, ref):
            author = submission.get("author", {})
            when = submission.get("creationTimeSeconds")
            if (author.get("participantType") == "CONTESTANT" and
                type(when) is int and meta["startTimeSeconds"] <= when <
                meta["startTimeSeconds"] + meta["durationSeconds"] and
                submission.get("verdict") in (None, "TESTING")):
                count += 1
    return count


def freeze_snapshot(root, manifest):
    # The mutable checkpoint is only a pointer; each finalized/checkpoint version is immutable.
    sha = digest(manifest)
    path = Path(root) / "snapshots" / f"{sha}.json"
    if not path.exists():
        write_json(path, manifest)
    write_json(Path(root) / "snapshot.json", manifest)
    return sha


def acquisition_coverage(manifest):
    selected=len(manifest["selection"]["contest_ids"])
    complete=sum(bool(e.get("complete")) for e in manifest["contests"].values())
    minimum=complete>=manifest["config"]["minimum_contests"]
    return {"selected":selected,"complete":complete,"incomplete":selected-complete,
            "minimum_complete_count_met":minimum,
            "status":"requires_preparation" if minimum else "pilot_only"}


def collect(root, config, *, pilot=False, offline=False, client=None):
    root = Path(root)
    client = client or ApiClient(root, config, offline=offline)
    selection_path = root / "selection.json"
    if selection_path.exists():
        selection = read_json(selection_path)
        if selection["config_sha256"] != digest(config) or selection["pilot"] != pilot:
            raise StudyError("Selection/config already frozen; use a new work directory")
    else:
        contests, list_ref = client.request("contest.list", {"gym": "false"})
        if not isinstance(contests, list) or len({c["id"] for c in contests}) != len(contests):
            raise StudyError("Malformed/conflicting contest.list")
        count = config["pilot_contests"] if pilot else config["target_contests"]
        screening_sha = None
        if config.get("selection_policy") == POLICY:
            candidates = select_contests(contests, config, len(contests))
            ids, screening_sha = screen_selection(root, client, config, contests, list_ref, candidates, count)
        else:
            ids = select_contests(contests, config, count)
        if not ids:
            raise StudyError("No eligible contests with verified dates")
        selection = {"schema_version": 1, "config_sha256": digest(config), "pilot": pilot,
                     "seed": config["seed"], "contest_ids": ids, "list_ref": list_ref,
                     "method": "seeded-sha256-round-robin-division; no outcome selection"}
        if screening_sha:
            selection.update(selection_policy=POLICY, screening_sha256=screening_sha)
        write_json(selection_path, selection)
    manifest_path = root / "snapshot.json"
    manifest = read_json(manifest_path) if manifest_path.exists() else {
        "schema_version": 1, "data_kind": client.data_kind, "config": config,
        "selection": selection, "contests": {}}
    if manifest["config"] != config or manifest["selection"] != selection:
        raise StudyError("Snapshot/selection mismatch")
    contests = {c["id"]: c for c in raw_json(root, selection["list_ref"])}
    if "problems_ref" not in manifest:
        _, manifest["problems_ref"] = client.request("problemset.problems")
        freeze_snapshot(root, manifest)
    for contest_id in selection["contest_ids"]:
        key = str(contest_id)
        if manifest["contests"].get(key, {}).get("complete"):
            previous = manifest["contests"][key]
            for ref in [previous["rating_changes_ref"], *previous["standings_refs"], *previous["status_refs"]]:
                raw_json(root, ref)
            continue
        entry = {"complete": False}
        manifest["contests"][key] = entry
        try:
            _, entry["rating_changes_ref"] = client.request("contest.ratingChanges", {"contestId": contest_id})
            entry["standings_refs"] = _pages(client, "contest.standings", contest_id, config)
            entry["standings_scope"] = "full_official_response; contestId only, no from/count/showUnofficial"
            entry["status_refs"] = _pages(client, "contest.status", contest_id, config)
            pending = _pending(root, entry["status_refs"], contests[contest_id])
            for _ in range(config["pending_rechecks"]):
                if not pending or offline:
                    break
                # Refresh pending pages; old bytes/provenance remain immutable and addressable.
                for i, ref in enumerate(entry["status_refs"]):
                    if _pending(root, [ref], contests[contest_id]):
                        _, entry["status_refs"][i] = client.request("contest.status", ref["params"], refresh=True)
                pending = _pending(root, entry["status_refs"], contests[contest_id])
            entry["pending_verdicts"] = pending
            entry["complete"] = pending == 0
            if pending:
                entry["error"] = "Unresolved official verdicts; contest not usable"
        except StudyError as error:
            entry["error"] = str(error)
            if getattr(error, "stop_collection", False):
                manifest["coverage"] = {**acquisition_coverage(manifest), "stop_reason": str(error)}
                freeze_snapshot(root, manifest)
                raise
        freeze_snapshot(root, manifest)
    manifest["coverage"] = acquisition_coverage(manifest)
    snapshot_sha = freeze_snapshot(root, manifest)
    return {"snapshot_sha256": snapshot_sha, "data_kind": manifest["data_kind"],
            "api_calls_this_run": client.network_requests,
            "network_requests_this_run": client.network_requests if client.data_kind == "real" else 0,
            **manifest["coverage"]}
