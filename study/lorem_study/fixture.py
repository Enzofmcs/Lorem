"""Generated API-shaped synthetic data only. Never a Codeforces research result."""

import json
from pathlib import Path
import urllib.parse

from .api import ApiClient
from .collect import collect
from .io import StudyError, canonical


def fixture_api(count=10):
    contests, catalog, entries = [], [], {}
    for n in range(count):
        cid, start = 900000 + n, 1593561600 + n * 86400
        contest = {"id": cid, "name": f"SYNTHETIC ONLY {n} Div. {1 + n % 2}", "type": "CF",
                   "phase": "FINISHED", "startTimeSeconds": start, "durationSeconds": 7200}
        contests.append(contest)
        problems = [{"contestId": cid, "index": index, "name": f"Synthetic {index}", "rating": rating}
                    for index, rating in zip("ABC", (800, 1200, 1600))]
        catalog.extend(problems)
        standings, ratings, submissions = [], [], []
        for participant, rating, sequence in (
            ("synthetic_sequential", 1200, [("A", 300, "WRONG_ANSWER"), ("A", 600, "OK"),
                                           ("B", 1200, "WRONG_ANSWER"), ("B", 1680, "OK"), ("C", 3900, "OK")]),
            ("synthetic_without_ac", 1400, [("A", 600, "OK"), ("B", 1200, "WRONG_ANSWER")]),
            ("synthetic_skip", 1200, [("B", 1000, "WRONG_ANSWER"), ("B", 1800, "OK")]),
            ("synthetic_interleaved", 1200, [("A", 500, "WRONG_ANSWER"), ("B", 700, "WRONG_ANSWER"),
                                            ("A", 900, "OK"), ("B", 1300, "OK")]),
            ("synthetic_novice", 0, [("A", 900, "OK")]),
        ):
            party = {"contestId": cid, "participantType": "CONTESTANT", "members": [{"handle": participant}]}
            standings.append({"party": party, "rank": 1, "points": 0})
            ratings.append({"contestId": cid, "handle": participant, "oldRating": rating, "newRating": 9999, "rank": 1})
            for index, offset, verdict in sequence:
                submissions.append({"id": cid * 100 + len(submissions), "contestId": cid,
                                    "author": party, "creationTimeSeconds": start + offset,
                                    "problem": problems[ord(index) - 65], "verdict": verdict})
        entries[cid] = {"standings": {"contest": contest, "problems": problems, "rows": standings},
                        "ratings": ratings, "submissions": sorted(submissions, key=lambda s: s["id"], reverse=True)}

    def transport(url, timeout):
        parsed = urllib.parse.urlsplit(url)
        method = parsed.path.rsplit("/", 1)[-1]
        params = dict(urllib.parse.parse_qsl(parsed.query))
        if method == "contest.list":
            result = contests
        elif method == "problemset.problems":
            result = {"problems": catalog, "problemStatistics": []}
        else:
            entry = entries[int(params["contestId"])]
            if method == "contest.ratingChanges":
                result = entry["ratings"]
            elif method == "contest.standings" and set(params) == {"contestId"}:
                result = entry["standings"]
            else:
                first, size = int(params["from"]) - 1, int(params["count"])
                if method == "contest.standings":
                    result = {**entry["standings"], "rows": entry["standings"]["rows"][first:first + size]}
                elif method == "contest.status":
                    result = entry["submissions"][first:first + size]
                else:
                    raise AssertionError(method)
        return 200, {}, canonical({"status": "OK", "result": result})
    return transport


def create_fixture(root, config, count=10):
    if not 1 <= count <= config["target_contests"]:
        raise StudyError("Fixture count outside configured target")
    if Path(root).exists() and any(Path(root).iterdir()):
        raise StudyError("Fixture requires an empty directory; never overwrite a real snapshot")
    clock = [0.0]

    def sleep(seconds):
        clock[0] += seconds

    client = ApiClient(root, config, transport=fixture_api(count), monotonic=lambda: clock[0], sleep=sleep,
                       now=lambda: "2020-07-01T00:00:00Z", data_kind="synthetic")
    return collect(root, config, client=client)
