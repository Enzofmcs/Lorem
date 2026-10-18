"""Screen only contest metadata/problem structure before freezing a real cohort."""

from pathlib import Path
import re

from .io import StudyError, digest, read_json, write_json

POLICY = "regular-simple-v1"


def regular_round(contest):
    name = contest.get("name", "")
    return (contest.get("type") == "CF" and isinstance(name, str) and
            bool(re.search(r"\bDiv\.?\s*[1-4]\b|\bCodeforces Global Round\b", name, re.I)) and
            not re.search(r"\b(?:unrated|april fools|marathon|team contest)\b", name, re.I))


def simple_order(problems):
    indices = [p.get("index") for p in problems]
    return bool(indices) and len(indices) <= 26 and indices == [chr(65 + i) for i in range(len(indices))]


def screen_selection(root, client, config, contests, list_ref, candidates, count):
    """No submission, score, AC count, or model result is consulted here."""
    root = Path(root)
    path = root / "screening.json"
    checkpoint = {"schema_version": 1, "policy": POLICY, "config_sha256": digest(config),
                  "list_ref": list_ref, "candidate_ids": candidates, "decisions": []}
    if path.exists():
        previous = read_json(path)
        if any(previous.get(k) != v for k, v in checkpoint.items() if k != "decisions"):
            raise StudyError("Screening configuration/list changed; use another work directory")
        checkpoint = previous
    metadata = {c["id"]: c for c in contests}
    decisions = checkpoint["decisions"]
    if [d["contest_id"] for d in decisions] != candidates[:len(decisions)]:
        raise StudyError("Screening candidate order mismatch")
    selected = [d["contest_id"] for d in decisions if d["eligible_structure"]]
    for cid in candidates[len(decisions):config.get("max_screened_candidates", 100)]:
        if len(selected) >= count:
            break
        decision = {"contest_id": cid, "eligible_structure": False}
        if not regular_round(metadata[cid]):
            decision["reason"] = "not_recognized_regular_cf_round"
        else:
            result, ref = client.request("contest.standings", {"contestId": cid})
            if (not isinstance(result, dict) or not isinstance(result.get("problems"), list) or
                not isinstance(result.get("rows"), list) or not isinstance(result.get("contest"), dict)):
                raise StudyError("Malformed screening standings; resume without skipping this candidate")
            if any(result["contest"].get(k) != metadata[cid].get(k) for k in
                   ("id", "type", "phase", "startTimeSeconds", "durationSeconds")):
                raise StudyError("Screening contest metadata mismatch; candidate not silently replaced")
            decision.update(standings_ref=ref, problem_indices=[p.get("index") for p in result["problems"]])
            decision["eligible_structure"] = simple_order(result["problems"])
            decision["reason"] = None if decision["eligible_structure"] else "unsupported_problem_order"
        decisions.append(decision)
        write_json(path, checkpoint)
        if decision["eligible_structure"]:
            selected.append(cid)
    if len(selected) != count:
        raise StudyError(f"Only {len(selected)}/{count} contests pass structural screening; selection not frozen")
    archived = root / "screenings" / f"{digest(checkpoint)}.json"
    if not archived.exists():
        write_json(archived, checkpoint)
    return selected, digest(checkpoint)
