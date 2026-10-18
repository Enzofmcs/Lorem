"""Auditable observed attempts, conservative sequential intervals and right censoring."""

from collections import Counter, defaultdict
from pathlib import Path

from .config import cutoff_seconds
from .io import StudyError, check_version, digest, raw_json, read_json, write_json, write_rows


def individual(party):
    if not isinstance(party, dict):
        return None
    members = party.get("members", [])
    if (party.get("participantType") != "CONTESTANT" or party.get("teamId") is not None or
        not isinstance(members, list) or len(members) != 1 or not isinstance(members[0], dict) or
        not isinstance(members[0].get("handle"), str)):
        return None
    return members[0]["handle"].strip().casefold() or None


def _unique(items, key):
    found, conflicts, duplicates = {}, set(), 0
    for item in items:
        k = key(item)
        if k in found:
            if found[k] != item:
                conflicts.add(k)
            else:
                duplicates += 1
        else:
            found[k] = item
    return found, conflicts, duplicates


def _intervals(submissions, indices, start, end):
    """Only completed uncontaminated earlier intervals survive a later violation."""
    by_problem = defaultdict(list)
    for submission in submissions:
        by_problem[submission["problem"]["index"]].append(submission)
    result, blocked, anchor = {}, False, start
    for index in indices:
        rows = sorted(by_problem.get(index, []), key=lambda x: (x["creationTimeSeconds"], x["id"]))
        if not rows:
            blocked = True
            continue
        first_ac = min((r["creationTimeSeconds"] for r in rows if r.get("verdict") == "OK"), default=None)
        stop = first_ac if first_ac is not None else end
        reason = None
        if blocked:
            reason = "predecessor_not_valid"
        elif first_ac is not None and first_ac <= anchor:
            reason = "inverted_or_nonpositive_ac"
        elif any(r["creationTimeSeconds"] < anchor for r in rows):
            reason = "submission_before_anchor"
        else:
            # The predecessor's anchoring AC is excluded; later resubmissions contaminate.
            foreign = [r for r in submissions if r["problem"]["index"] != index and
                       (anchor < r["creationTimeSeconds"] <= stop or
                        (anchor == start and r["creationTimeSeconds"] == start))]
            if foreign:
                reason = "interleaved_or_skipped_problem"
            elif any(r.get("verdict") in (None, "TESTING") for r in rows):
                reason = "unresolved_verdict"
        if stop <= anchor:
            reason = reason or "nonpositive_window"
        last = max(r["creationTimeSeconds"] for r in rows if r["creationTimeSeconds"] <= stop)
        result[index] = {"first_ac_seconds": first_ac, "anchor_seconds": anchor if not blocked else None,
                         "observed_end_seconds": stop if not reason else None,
                         "duration_seconds": stop - anchor if not reason else None,
                         "available_seconds": end - anchor if not blocked else None,
                         "event": int(first_ac is not None) if not reason else None,
                         "sensitivity_last_submission_seconds": last - anchor if not reason and last > anchor else None,
                         "reason": reason}
        if reason or first_ac is None:
            blocked = True
        else:
            anchor = first_ac
    for index in by_problem.keys() - result.keys():
        result[index] = {"reason": "unsupported_problem_order"}
    return result


def prepare(root):
    root = Path(root)
    snapshot = read_json(root / "snapshot.json")
    check_version(snapshot)
    config = snapshot["config"]
    metadata = {c["id"]: c for c in raw_json(root, snapshot["selection"]["list_ref"])}
    catalog_result = raw_json(root, snapshot["problems_ref"])
    if not isinstance(catalog_result, dict) or not isinstance(catalog_result.get("problems"), list):
        raise StudyError("Malformed problem catalog")
    catalog, catalog_conflicts, _ = _unique(catalog_result["problems"], lambda p: (p.get("contestId"), p.get("index")))
    features, labels, audit, contest_reports = [], [], [], []
    all_reasons, submission_exclusions = Counter(), Counter()
    complete_ids, origin_links = [], []
    for key, entry in sorted(snapshot["contests"].items(), key=lambda x: int(x[0])):
        contest_id = int(key)
        meta = metadata.get(contest_id, {})
        if not entry.get("standings_refs") or not entry.get("status_refs"):
            contest_reports.append({"contest_id": contest_id, "complete": False,
                                    "reason": "incomplete_collection", "observed_attempts": None})
            continue
        pages = [raw_json(root, r) for r in entry["standings_refs"]]
        problems = pages[0]["problems"]
        inconsistent_pages = (any(p["problems"] != problems or p["contest"] != pages[0]["contest"] for p in pages) or
                              any(pages[0]["contest"].get(field) != meta.get(field) for field in
                                  ("id", "startTimeSeconds", "durationSeconds", "type", "phase")))
        start, duration = meta.get("startTimeSeconds"), meta.get("durationSeconds")
        meta_ok = type(start) is int and type(duration) is int and duration > 0
        end = start + duration if meta_ok else 0
        order = [p.get("index") for p in problems]
        order_ok = bool(order) and len(order) <= 26 and order == [chr(65 + i) for i in range(len(order))]
        rows = [row for p in pages for row in p["rows"]]
        standings = defaultdict(list)
        for row in rows:
            handle = individual(row.get("party", {}))
            if handle is not None:
                if row not in standings[handle]:
                    standings[handle].append(row)
        changes = raw_json(root, entry["rating_changes_ref"]) if entry.get("rating_changes_ref") else []
        if not isinstance(changes, list):
            raise StudyError("Malformed ratingChanges")
        ratings = defaultdict(list)
        for change in changes:
            if isinstance(change.get("handle"), str):
                ratings[change["handle"].strip().casefold()].append(change)
        raw_submissions = [s for ref in entry["status_refs"] for s in raw_json(root, ref)]
        unique, conflicts, duplicate_count = _unique(raw_submissions, lambda s: s.get("id"))
        by_person = defaultdict(list)
        conflict_people = {individual(s.get("author", {})) for s in raw_submissions if s.get("id") in conflicts}
        for sid, sub in unique.items():
            handle = individual(sub.get("author", {}))
            when = sub.get("creationTimeSeconds")
            reason = None
            if handle is None:
                reason = "not_official_individual"
            elif type(sid) is not int or type(when) is not int or not meta_ok:
                reason = "invalid_identity_or_timestamp"
                conflict_people.add(handle)
            elif not start <= when < end:
                reason = "outside_contest_window"
            elif sub.get("contestId") != contest_id or sub.get("author", {}).get("contestId") != contest_id:
                reason = "inconsistent_contest_identity"
                conflict_people.add(handle)
            elif not isinstance(sub.get("problem", {}).get("index"), str):
                reason = "invalid_problem_identity"
                conflict_people.add(handle)
            if reason:
                submission_exclusions[reason] += 1
                continue
            by_person[handle].append(sub)
        reasons, valid_events, valid_censored, observed = Counter(), 0, 0, 0
        links = set()
        for handle, submissions in sorted(by_person.items()):
            person = digest(["lorem-public-participant-v1", handle])
            changes_for_person = ratings.get(handle, [])
            rating_values = {r.get("oldRating") for r in changes_for_person if type(r.get("oldRating")) is int}
            old_rating = next(iter(rating_values)) if len(rating_values) == 1 else None
            person_reason = None
            if not entry["complete"] or inconsistent_pages:
                person_reason = "incomplete_collection"
            elif handle in conflict_people:
                person_reason = "conflicting_submission_identity"
            elif len(standings.get(handle, [])) != 1:
                person_reason = "missing_or_conflicting_official_participation"
            elif not changes_for_person or all(r.get("oldRating") is None for r in changes_for_person):
                person_reason = "missing_old_rating"
            elif (len(rating_values) != 1 or any(type(r.get("oldRating")) is not int for r in changes_for_person) or
                  any(r.get("contestId") != contest_id for r in changes_for_person)):
                person_reason = "conflicting_rating_changes"
            elif old_rating <= 0:
                person_reason = "insufficient_prior_rating_history"
            elif not order_ok:
                person_reason = "unsupported_problem_order"
            intervals = _intervals(submissions, order if order_ok else [], start, end)
            for index, interval in sorted(intervals.items()):
                observed += 1
                problem = next((p for p in problems if p.get("index") == index), {})
                origin = problem.get("contestId")
                origin_meta = metadata.get(origin, {})
                origin_start = origin_meta.get("startTimeSeconds")
                catalog_problem = catalog.get((origin, index), {})
                problem_rating = catalog_problem.get("rating")
                identity_ok = all(s["problem"].get("contestId") == origin for s in submissions if s["problem"]["index"] == index)
                reason = person_reason
                if not reason and (type(origin) is not int or not identity_ok or (origin, index) in catalog_conflicts):
                    reason = "ambiguous_problem_origin"
                if not reason and (type(origin_start) is not int or origin_start < cutoff_seconds(config)):
                    reason = "origin_date_missing_or_before_cutoff"
                if not reason and (type(problem_rating) is not int or problem_rating <= 0):
                    reason = "missing_problem_rating"
                reason = reason or interval.get("reason")
                sample_id = digest([contest_id, person, origin, index])
                features.append({"sample_id": sample_id, "contest_id": contest_id,
                                 "participant_key": person, "problem_contest_id": origin, "problem_index": index,
                                 "old_rating": old_rating, "problem_rating": problem_rating,
                                 "delta": problem_rating - old_rating if type(problem_rating) is int and type(old_rating) is int else None})
                quality = "valid" if not reason else "excluded"
                labels.append({"sample_id": sample_id, "contest_id": contest_id, "quality": quality,
                               "reason": reason, "duration_seconds": interval.get("duration_seconds") if not reason else None,
                               "event": interval.get("event") if not reason else None,
                               "sensitivity_last_submission_seconds": interval.get("sensitivity_last_submission_seconds") if not reason else None})
                audit.append({"sample_id": sample_id, "contest_id": contest_id, "participant_key": person,
                              "problem_index": index, "origin_start_seconds": origin_start,
                              "origin_metadata_ref": snapshot["selection"]["list_ref"],
                              "problem_rating_ref": snapshot["problems_ref"], "rating_changes_ref": entry.get("rating_changes_ref"),
                              "submission_ids": sorted(s["id"] for s in submissions if s["problem"]["index"] == index),
                              "start_seconds": start, "end_seconds": end, "quality": quality, **interval,
                              "reason": reason, "protocol_version": config["protocol_version"],
                              "eligibility_version": config["eligibility_version"]})
                if reason:
                    reasons[reason] += 1
                elif interval["event"]:
                    valid_events += 1
                else:
                    valid_censored += 1
                if type(origin) is int:
                    links.add(origin)
        # Acquisition completeness is distinct from the eligible contest population.
        has_population = any(len(standings.get(h, [])) == 1 and len({r.get("oldRating") for r in rs
                             if type(r.get("oldRating")) is int}) == 1 and
                             all(type(r.get("oldRating")) is int and r["oldRating"] > 0 and
                                 r.get("contestId") == contest_id for r in rs)
                             for h, rs in ratings.items())
        has_problem = any(type(p.get("contestId")) is int and
                          type(metadata.get(p["contestId"], {}).get("startTimeSeconds")) is int and
                          metadata[p["contestId"]]["startTimeSeconds"] >= cutoff_seconds(config) and
                          type(catalog.get((p["contestId"], p.get("index")), {}).get("rating")) is int and
                          catalog[(p["contestId"], p.get("index"))]["rating"] > 0 for p in problems)
        usable = (entry["complete"] and not inconsistent_pages and order_ok and has_population and has_problem and
                  meta.get("type") == "CF" and meta.get("phase") == "FINISHED" and meta_ok and start >= cutoff_seconds(config))
        if usable:
            complete_ids.append(contest_id)
        origin_links.append({"contest_ids": sorted({contest_id} | links), "source": "verified problem contestId in standings"})
        all_reasons.update(reasons)
        contest_reports.append({"contest_id": contest_id, "complete": bool(entry["complete"]) and not inconsistent_pages,
                                "eligible_for_split": bool(usable), "simple_problem_order": order_ok,
                                "participations_all": len(rows), "official_individuals": len(standings),
                                "participants_with_observed_attempts": len(by_person), "raw_submissions": len(raw_submissions),
                                "duplicate_submissions": duplicate_count, "conflicting_submission_ids": len(conflicts),
                                "observed_attempts": observed, "valid_events": valid_events,
                                "valid_censored": valid_censored, "excluded": sum(reasons.values()), "reasons": dict(reasons)})
    output = root / "prepared"
    for name, values in (("features", features), ("labels", labels), ("audit", audit)):
        write_rows(output / f"{name}.jsonl", values)
    valid = [r for r in labels if r["quality"] == "valid"]
    # Predefined, non-temporal slices. None is a separate missing-data group.
    slices = {field: defaultdict(Counter) for field in ("old_rating", "problem_rating", "delta")}
    label_by_id = {r["sample_id"]: r for r in labels}
    for f in features:
        label = label_by_id[f["sample_id"]]
        for field, bands in slices.items():
            band = "missing" if f[field] is None else str((f[field] // 400) * 400)
            bands[band]["observed"] += 1
            bands[band]["excluded" if label["quality"] != "valid" else ("event" if label["event"] else "censored")] += 1
    report = {"schema_version": 1, "data_kind": snapshot["data_kind"], "snapshot_sha256": digest(snapshot),
              "config": config, "complete_contest_ids": sorted(complete_ids),
              "origin_links": origin_links + config["shared_contest_groups"],
              "observed_attempts": len(labels), "valid_events": sum(r["event"] for r in valid),
              "valid_censored": sum(1 - r["event"] for r in valid), "excluded": len(labels) - len(valid),
              "coverage": len(valid) / len(labels) if labels else None,
              "reasons": dict(all_reasons), "submission_exclusions": dict(submission_exclusions),
              "by_participant_rating_band_400": {k: dict(v) for k, v in sorted(slices["old_rating"].items())},
              "by_problem_rating_band_400": {k: dict(v) for k, v in sorted(slices["problem_rating"].items())},
              "by_delta_band_400": {k: dict(v) for k, v in sorted(slices["delta"].items())},
              "contests": contest_reports, "features_sha256": digest(features), "labels_sha256": digest(labels),
              "limitations": ["Sequential wall-clock approximation, not effective dedication",
                              "Informative censoring/abandonment and selected prefixes",
                              "Problem rating available at collection, not guaranteed historical",
                              "Public handle hashes are pseudonyms, not anonymization"]}
    write_json(output / "manifest.json", report)
    return report
