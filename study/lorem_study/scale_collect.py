"""Bounded, compressed acquisition for scale-t50-v1; never relax eligibility."""

import argparse
import gzip
import hashlib
from pathlib import Path
import re
import shutil
import time
import urllib.error
import urllib.request

from .api import ApiClient, METHODS, utc_now
from .collect import acquisition_coverage, collect, eligible_contest, freeze_snapshot, select_contests
from .config import cutoff_seconds, load_config
from .io import StudyError, digest, put_bytes, raw_json, read_json, write_json
from .selection import regular_round, simple_order


class BudgetExceeded(StudyError):
    stop_collection = True


class BoundedTransport:
    """Durable gate/budgets across restarts. The caller holds the directory lock."""

    def __init__(self, root, limits, interval=2, *, clock=time.time, sleep=time.sleep,
                 opener=urllib.request.urlopen):
        self.root, self.limits = Path(root), limits
        self.clock, self.sleep, self.opener, self.interval = clock, sleep, opener, interval
        self.path = self.root / "network-budget.json"
        self.state = read_json(self.path) if self.path.exists() else {
            "attempts": 0, "http_bytes": 0, "last_start": None,
            "first_start_utc": None, "minimum_start_gap_seconds": None}
        self.raw_bytes = sum(p.stat().st_size for p in (self.root / "raw").glob("*"))

    def __call__(self, url, timeout):
        if self.state["attempts"] >= self.limits["max_network_attempts"]:
            raise BudgetExceeded("Network attempt budget exhausted; resume with a separately reviewed budget")
        if self.raw_bytes + self.limits["max_response_bytes"] > self.limits["max_raw_compressed_bytes"]:
            raise BudgetExceeded("Compressed raw budget cannot fit a worst-case next response")
        if shutil.disk_usage(self.root).free < self.limits["min_free_disk_bytes"] + self.limits["max_response_bytes"]:
            raise BudgetExceeded("Insufficient free disk reserve; acquisition paused")
        previous = self.state["last_start"]
        if previous is not None:
            self.sleep(max(0, self.interval - (self.clock() - previous)))
        started = self.clock()
        if previous is not None:
            gap = started - previous
            current = self.state["minimum_start_gap_seconds"]
            self.state["minimum_start_gap_seconds"] = gap if current is None else min(current, gap)
        self.state.update(last_start=started, attempts=self.state["attempts"] + 1)
        self.state["first_start_utc"] = self.state["first_start_utc"] or utc_now()
        write_json(self.path, self.state)  # count attempts even on transport failure
        request = urllib.request.Request(url, headers={"User-Agent": "LoremStudy/scale-t50-v1"})
        try:
            response = self.opener(request, timeout=timeout)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            body = response.read(self.limits["max_response_bytes"] + 1)
            if len(body) > self.limits["max_response_bytes"]:
                raise BudgetExceeded("HTTP body exceeds per-response memory/disk bound; not cached as complete")
            status, headers = response.code, dict(response.headers)
        self.state["http_bytes"] += len(body)
        self.state["last_response_utc"] = utc_now()
        sha = hashlib.sha256(body).hexdigest()
        if not (self.root / "raw" / f"{sha}.json.gz").exists():
            self.raw_bytes += len(gzip.compress(body, compresslevel=5, mtime=0))
        write_json(self.path, self.state)
        return status, headers, body


def import_cache(root, source):
    """Preserve original request dates/identities; refresh only universe metadata later."""
    root, source = Path(root), Path(source)
    if (root / "cache-import.json").exists():
        record = read_json(root / "cache-import.json")
        if record["source_path"] != str(source.resolve()):
            raise StudyError("Cache import source changed")
        return record
    source_snapshot=read_json(source/"snapshot.json")
    if source_snapshot.get("data_kind")!="real":
        raise StudyError("Only a real snapshot may be imported into the real scale acquisition")
    target = read_json(root / "cache.json") if (root / "cache.json").exists() else {}
    imported = []
    for key, ref in read_json(source / "cache.json").items():
        if ref["method"] in ("contest.list", "problemset.problems"):
            continue
        if (ref.get("data_kind","real")!="real" or ref.get("http_status")!=200 or
                ref["method"] not in METHODS or key!=digest({"method":ref["method"],"params":ref["params"]})):
            raise StudyError("Imported cache kind/request identity/status mismatch")
        raw_json(source, ref)  # verify before reuse
        plain = source / "raw" / f"{ref['sha256']}.json"
        data = plain.read_bytes() if plain.exists() else gzip.decompress(plain.with_suffix(".json.gz").read_bytes())
        if put_bytes(root, data, compress=True) != ref["sha256"]:
            raise StudyError("Cache import changed HTTP bytes")
        if key in target and target[key] != ref:
            raise StudyError("Cache import would overwrite a different response")
        target[key] = ref
        imported.append(ref)
    write_json(root / "cache.json", target)
    record = {"source_path": str(source.resolve()), "imported_at_utc": utc_now(),
              "source_snapshot_sha256": digest(source_snapshot),
              "objects": imported, "note": "original dates retained; not freshly acquired"}
    write_json(root / "cache-import.json", record)
    return record


def freeze_universe(root, client, config):
    root = Path(root)
    path = root / "universe.json"
    if path.exists():
        universe = read_json(path)
        if universe["config_sha256"] != digest(config):
            raise StudyError("Universe/config mismatch")
        return universe
    contests, list_ref = client.request("contest.list", {"gym": "false"}, refresh=True)
    catalog, problems_ref = client.request("problemset.problems", refresh=True)
    if (not isinstance(contests, list) or len({c["id"] for c in contests}) != len(contests)
            or not isinstance(catalog, dict) or not isinstance(catalog.get("problems"), list)):
        raise StudyError("Malformed universe metadata")
    indices = {}
    for problem in catalog["problems"]:
        indices.setdefault(problem.get("contestId"), set()).add(problem.get("index"))
    candidates = select_contests(contests, config, len(contests))
    versioned = [cid for cid in candidates if any(
        isinstance(i, str) and re.fullmatch("[A-Z]1", i) and i[0] + "2" in indices.get(cid, set())
        for i in indices.get(cid, set()))]
    regular = [c["id"] for c in contests if eligible_contest(c, cutoff_seconds(config)) and regular_round(c)]
    upper = len(set(regular) - set(versioned))
    universe = {"schema_version": 1, "config_sha256": digest(config), "frozen_at_utc": utc_now(),
                "list_ref": list_ref, "problems_ref": problems_ref, "candidate_ids": candidates,
                "regular_ids": sorted(regular), "proven_versioned_ids": sorted(versioned),
                "metadata_eligible": len(candidates), "regular": len(regular),
                "upper_bound_before_standings": upper,
                "target": config["target_contests"], "minimum_target_shortfall": max(0, config["target_contests"] - upper),
                "selection_policy": "metadata plus full standings structure only; no outcomes used"}
    write_json(path, universe)
    return universe


def freeze_scale_selection(root, client, config, universe):
    root = Path(root)
    if (root / "selection.json").exists():
        selection = read_json(root / "selection.json")
        if selection["config_sha256"] != digest(config):
            raise StudyError("Frozen selection/config mismatch")
        return selection
    contests = {c["id"]: c for c in raw_json(root, universe["list_ref"])}
    path = root / "screening.json"
    record = read_json(path) if path.exists() else {
        "schema_version": 1, "universe_sha256": digest(universe), "decisions": []}
    if record["universe_sha256"] != digest(universe):
        raise StudyError("Screening universe changed")
    candidates = universe["candidate_ids"]
    if len(candidates) > config["max_screened_candidates"]:
        raise StudyError("Candidate budget too small: freeze an explicit revised plan")
    if [d["contest_id"] for d in record["decisions"]] != candidates[:len(record["decisions"])]:
        raise StudyError("Screening checkpoint order mismatch")
    for cid in candidates[len(record["decisions"]):]:
        decision = {"contest_id": cid, "eligible_structure": False}
        if cid not in universe["regular_ids"]:
            decision["reason"] = "not_recognized_regular_cf_round"
        elif cid in universe["proven_versioned_ids"]:
            decision["reason"] = "catalog_proves_two_versions; entire_contest_excluded"
        else:
            result, ref = client.request("contest.standings", {"contestId": cid})
            if (not isinstance(result, dict) or not isinstance(result.get("problems"), list)
                    or not isinstance(result.get("rows"), list) or not isinstance(result.get("contest"), dict)):
                raise StudyError("Malformed structural screening; no candidate replacement")
            if any(result["contest"].get(k) != contests[cid].get(k) for k in
                   ("id", "type", "phase", "startTimeSeconds", "durationSeconds")):
                raise StudyError("Screening metadata changed; no candidate replacement")
            decision.update(standings_ref=ref, problem_indices=[p.get("index") for p in result["problems"]],
                            eligible_structure=simple_order(result["problems"]))
            decision["reason"] = None if decision["eligible_structure"] else "unsupported_problem_order"
        record["decisions"].append(decision)
        write_json(path, record)
        if len(record["decisions"]) % 25 == 0:
            print(f"screened={len(record['decisions'])}/{len(candidates)} eligible={sum(d['eligible_structure'] for d in record['decisions'])}", flush=True)
    ids = [d["contest_id"] for d in record["decisions"] if d["eligible_structure"]][:config["target_contests"]]
    if not ids:
        raise StudyError("No structurally eligible contests")
    selection = {"schema_version": 1, "config_sha256": digest(config), "pilot": False,
                 "seed": config["seed"], "contest_ids": ids, "list_ref": universe["list_ref"],
                 "selection_policy": "regular-simple-v1", "screening_sha256": digest(record),
                 "method": "seeded-sha256-round-robin-division; full metadata/structure universe; no outcome selection",
                 "target": config["target_contests"], "shortfall": max(0, config["target_contests"] - len(ids)),
                 "shortfall_reason": "metadata/structure universe exhausted without relaxing eligibility" if len(ids) < config["target_contests"] else None}
    write_json(root / "selection.json", selection)
    return selection


def replay_scale(root,config,client):
    """Reconstruct coverage/integrity offline without retrying known failures.

    Failed/missing contests retain their recorded states; they cannot turn into
    empty successes or have an HTTP failure relabelled as an offline cache miss.
    """
    root=Path(root)
    if not client.offline or client.network_requests:
        raise StudyError("Replay requires a fresh offline client")
    snapshot=read_json(root/"snapshot.json")
    selection=read_json(root/"selection.json")
    if snapshot["config"]!=config or selection!=snapshot["selection"] or selection["config_sha256"]!=digest(config):
        raise StudyError("Offline snapshot/config/selection mismatch")
    if set(snapshot["contests"])-{str(cid) for cid in selection["contest_ids"]}:
        raise StudyError("Snapshot contains contests outside its frozen selection")
    raw_json(root,selection["list_ref"]);raw_json(root,snapshot["problems_ref"])
    for cached in client.index.values():
        client.request(cached["method"],cached["params"])
    for key,entry in snapshot["contests"].items():
        refs=[*entry.get("standings_refs",[]),*entry.get("status_refs",[])]
        if entry.get("rating_changes_ref"):refs.append(entry["rating_changes_ref"])
        for ref in refs:
            raw_json(root,ref)
        if not entry.get("complete"):continue
        if (not entry.get("rating_changes_ref") or len(entry.get("standings_refs",[]))!=1 or
                not entry.get("status_refs") or entry.get("pending_verdicts")!=0):
            raise StudyError("Complete contest lacks acquisition evidence")
        for index,ref in enumerate(entry["status_refs"]):
            if ref["method"]!="contest.status" or ref["params"]!={"contestId":key,"from":str(index*config["page_size"]+1),"count":str(config["page_size"])}:
                raise StudyError("Offline status pagination identity mismatch")
            rows=raw_json(root,ref)
            last=index==len(entry["status_refs"])-1
            if not isinstance(rows,list) or (len(rows)>=config["page_size"] if last else len(rows)!=config["page_size"]):
                raise StudyError("Offline pagination is not complete")
    rebuilt={**snapshot,"coverage":acquisition_coverage(snapshot)}
    if "stop_reason" in snapshot["coverage"]:
        rebuilt["coverage"]["stop_reason"]=snapshot["coverage"]["stop_reason"]
    if digest(rebuilt)!=digest(snapshot):raise StudyError("Offline coverage reconstruction differs from frozen snapshot")
    sha=freeze_snapshot(root,rebuilt)
    return {"snapshot_sha256":sha,"data_kind":snapshot["data_kind"],"api_calls_this_run":0,
            "network_requests_this_run":0,**rebuilt["coverage"]}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--plan", type=Path, required=True)
    parser.add_argument("--import-cache", type=Path)
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--universe-only", action="store_true")
    args = parser.parse_args(argv)
    from .cli import work_lock
    config, plan = load_config(args.config), read_json(args.plan)
    if plan["config_sha256"] != digest(config):
        raise StudyError("Plan/config mismatch")
    args.root.mkdir(parents=True, exist_ok=True)
    with work_lock(args.root):
        plan_path = args.root / "scale-plan.json"
        if plan_path.exists() and read_json(plan_path) != plan:
            raise StudyError("Scale plan already frozen; use a new directory")
        write_json(plan_path, plan)
        if args.import_cache:
            print(import_cache(args.root, args.import_cache)["source_snapshot_sha256"], flush=True)
        bounded = BoundedTransport(args.root, plan["resources"], config["request_interval_seconds"])
        client = ApiClient(args.root, config, offline=args.offline, transport=bounded, compress_raw=True)
        universe = freeze_universe(args.root, client, config)
        print({k:v for k,v in universe.items() if k not in ("candidate_ids", "regular_ids", "proven_versioned_ids", "list_ref", "problems_ref")}, flush=True)
        if args.universe_only:
            return
        selection = freeze_scale_selection(args.root, client, config, universe)
        print({"selected":len(selection["contest_ids"]),"shortfall":selection["shortfall"]}, flush=True)
        try:
            result = replay_scale(args.root,config,client) if args.offline else collect(args.root, config, client=client)
        except BudgetExceeded as error:
            snapshot_path=args.root/"snapshot.json"
            if snapshot_path.exists():
                snapshot=read_json(snapshot_path)
                write_json(args.root/"acquisition.json",{"snapshot_sha256":digest(snapshot),"stopped":str(error),
                           "network_requests_this_run":client.network_requests,**snapshot["coverage"]})
            raise
        write_json(args.root / ("offline-replay.json" if args.offline else "acquisition.json"), result)
        print(result, flush=True)


if __name__ == "__main__":
    main()
