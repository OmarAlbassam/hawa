"""Sqlite-backed result cache.

Keyed on (experiment_id, model, prompt_hash, temperature, post_id, fewshot_ids_hash).
Stores the validated `SentimentResponse` JSON plus latency and any error.

The cache exists for two reasons:
1. **Resumability** — Groq's free tier rate-limits hard. A 100-post run can
   span 30+ minutes. If the runner crashes or you Ctrl-C, re-running picks
   up where it left off instead of re-billing the same calls.
2. **Reproducibility** — paper-table runs should be exactly the same numbers
   every time you regenerate them.

The schema is intentionally narrow. If you change the prompt template, the
prompt_hash changes, so old rows naturally stop matching — no migration
needed.
"""

from __future__ import annotations

import hashlib
import json
import sqlite3
import threading
from dataclasses import dataclass
from pathlib import Path

_SCHEMA = """
CREATE TABLE IF NOT EXISTS results (
    experiment_id   TEXT NOT NULL,
    model           TEXT NOT NULL,
    prompt_hash     TEXT NOT NULL,
    temperature     REAL NOT NULL,
    post_id         INTEGER NOT NULL,
    fewshot_hash    TEXT NOT NULL,
    -- Payload
    is_relevant     INTEGER,
    irrelevance_reason TEXT,
    pred_score      REAL,
    pred_emotion    TEXT,
    pred_aspect     TEXT,
    -- Bookkeeping
    error           TEXT,
    latency_ms      REAL,
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    PRIMARY KEY (experiment_id, model, prompt_hash, temperature, post_id, fewshot_hash)
);

CREATE INDEX IF NOT EXISTS results_by_experiment ON results(experiment_id);
"""


@dataclass(frozen=True)
class CacheKey:
    experiment_id: str
    model: str
    prompt_hash: str
    temperature: float
    post_id: int
    fewshot_hash: str


@dataclass
class CachedResult:
    is_relevant: bool | None
    irrelevance_reason: str | None
    pred_score: float | None
    pred_emotion: str | None
    pred_aspect: str | None
    error: str | None
    latency_ms: float | None


def hash_prompt(prompt: str) -> str:
    return hashlib.sha256(prompt.encode()).hexdigest()[:16]


def hash_fewshot_ids(post_ids: list[int]) -> str:
    if not post_ids:
        return "none"
    blob = ",".join(str(i) for i in sorted(post_ids))
    return hashlib.sha256(blob.encode()).hexdigest()[:16]


class ResultCache:
    """Thread-safe sqlite cache.

    The async runner calls this from inside `asyncio.to_thread`-style boundaries,
    so a single connection guarded by a lock is enough — no need for a pool.
    Sqlite's WAL mode lets concurrent readers proceed even when one writer is
    active.
    """

    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._conn = sqlite3.connect(self.path, check_same_thread=False)
        self._conn.execute("PRAGMA journal_mode=WAL")
        self._conn.execute("PRAGMA synchronous=NORMAL")
        self._conn.executescript(_SCHEMA)
        self._lock = threading.Lock()

    def get(self, key: CacheKey) -> CachedResult | None:
        with self._lock:
            row = self._conn.execute(
                """
                SELECT is_relevant, irrelevance_reason, pred_score, pred_emotion,
                       pred_aspect, error, latency_ms
                FROM results
                WHERE experiment_id=? AND model=? AND prompt_hash=?
                  AND temperature=? AND post_id=? AND fewshot_hash=?
                """,
                (
                    key.experiment_id, key.model, key.prompt_hash,
                    key.temperature, key.post_id, key.fewshot_hash,
                ),
            ).fetchone()
        if row is None:
            return None
        return CachedResult(
            is_relevant=bool(row[0]) if row[0] is not None else None,
            irrelevance_reason=row[1],
            pred_score=row[2],
            pred_emotion=row[3],
            pred_aspect=row[4],
            error=row[5],
            latency_ms=row[6],
        )

    def put(self, key: CacheKey, result: CachedResult) -> None:
        with self._lock:
            self._conn.execute(
                """
                INSERT OR REPLACE INTO results (
                    experiment_id, model, prompt_hash, temperature, post_id,
                    fewshot_hash, is_relevant, irrelevance_reason, pred_score,
                    pred_emotion, pred_aspect, error, latency_ms
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    key.experiment_id, key.model, key.prompt_hash,
                    key.temperature, key.post_id, key.fewshot_hash,
                    int(result.is_relevant) if result.is_relevant is not None else None,
                    result.irrelevance_reason,
                    result.pred_score, result.pred_emotion, result.pred_aspect,
                    result.error, result.latency_ms,
                ),
            )
            self._conn.commit()

    def count(self, experiment_id: str) -> int:
        with self._lock:
            row = self._conn.execute(
                "SELECT COUNT(*) FROM results WHERE experiment_id=?",
                (experiment_id,),
            ).fetchone()
        return int(row[0])

    def close(self) -> None:
        with self._lock:
            self._conn.close()


def serialize_for_hash(obj: object) -> str:
    """Stable JSON serialization for any cache-key component."""
    return json.dumps(obj, sort_keys=True, separators=(",", ":"))
