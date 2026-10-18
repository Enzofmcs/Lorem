"""Single gated API client, immutable raw responses, resumable verified cache."""

from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
import json
import math
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request

from .io import StudyError, digest, put_bytes, raw_json, read_json, write_json

BASE_URL = "https://codeforces.com/api/"
METHODS = {"contest.list", "contest.standings", "contest.status",
           "contest.ratingChanges", "problemset.problems", "user.rating"}


def utc_now():
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def http_transport(url, timeout):
    request = urllib.request.Request(url, headers={"User-Agent": "LoremStudy/0.1"})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, dict(response.headers), response.read()
    except urllib.error.HTTPError as error:
        return error.code, dict(error.headers), error.read()


class ApiClient:
    def __init__(self, root, config, *, offline=False, transport=http_transport,
                 monotonic=time.monotonic, sleep=time.sleep, now=utc_now, data_kind="real"):
        self.root, self.config, self.offline = Path(root), config, offline
        self.transport, self.clock, self.sleep, self.now = transport, monotonic, sleep, now
        self.last_request = None
        self.index_path = self.root / "cache.json"
        self.index = read_json(self.index_path) if self.index_path.exists() else {}
        self.network_requests = 0
        self.data_kind = data_kind

    def request(self, method, params=None, *, refresh=False):
        if method not in METHODS:
            raise StudyError("Unsupported API method")
        params = {str(k): str(v) for k, v in (params or {}).items()}
        key = digest({"method": method, "params": params})
        if key in self.index and (not refresh or self.offline):
            ref = self.index[key]
            if ref.get("data_kind", "real") != self.data_kind:
                raise StudyError("Cannot mix real and synthetic API cache")
            if ref.get("method") != method or ref.get("params") != params:
                raise StudyError("Cache request identity mismatch")
            return raw_json(self.root, ref), ref
        if self.offline:
            raise StudyError(f"Offline cache miss: {method} {params}")
        url = BASE_URL + method + "?" + urllib.parse.urlencode(sorted(params.items()))
        for attempt in range(self.config["max_retries"] + 1):
            if self.last_request is not None:
                self.sleep(max(0, self.config["request_interval_seconds"] - (self.clock() - self.last_request)))
            self.last_request = self.clock()
            self.network_requests += 1
            headers, body, status = {}, b"", None
            try:
                status, headers, body = self.transport(url, self.config["timeout_seconds"])
            except (OSError, urllib.error.URLError) as error:
                failure = f"Network failure: {error}"
            else:
                sha = put_bytes(self.root, body)
                ref = {"method": method, "params": params, "url": url,
                       "collected_at_utc": self.now(), "sha256": sha, "http_status": status,
                       "data_kind": self.data_kind}
                # Includes FAILED/error bodies, never interpreted as empty successful data.
                write_json(self.root / "responses" / f"{digest(ref)}.json", ref)
                try:
                    envelope = json.loads(body)
                except (ValueError, UnicodeDecodeError):
                    envelope = {}
                if not isinstance(envelope, dict):
                    envelope = {}
                if status == 200 and envelope.get("status") == "OK" and "result" in envelope:
                    self.index[key] = ref
                    write_json(self.index_path, self.index)
                    return envelope["result"], ref
                comment = str(envelope.get("comment", "invalid API response"))
                failure = f"{method}: HTTP {status}, {comment}"
                transient = status in (408, 429) or status >= 500 or (
                    envelope.get("status") == "FAILED" and "limit" in comment.lower())
                if not transient:
                    raise StudyError(failure)
            if attempt == self.config["max_retries"]:
                raise StudyError(failure)
            retry = next((v for k, v in headers.items() if k.lower() == "retry-after"), None)
            delay = 2 ** (attempt + 1)
            if retry:
                try:
                    delay = max(delay, float(retry))
                except ValueError:
                    try:
                        delay = max(delay, (parsedate_to_datetime(retry) -
                                           datetime.fromisoformat(self.now().replace("Z", "+00:00"))).total_seconds())
                    except (ValueError, TypeError):
                        pass
            if not math.isfinite(delay) or delay > 60:
                raise StudyError(f"Rate limited; Retry-After={retry!r}. Resume after the server delay; no early retry")
            self.sleep(delay)
