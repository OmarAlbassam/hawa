"""Typer CLI: `benchmark embed | run | report`.

Installed as the `benchmark` console script via pyproject.
"""

from __future__ import annotations

import asyncio
import logging
from pathlib import Path

import typer
from rich.console import Console
from rich.logging import RichHandler
from rich.table import Table

from benchmark.dataset import assign_folds, dataset_hash, load_dataset
from benchmark.import_csv import ColumnMap, import_csv_to_jsonl
from benchmark.report import plot_all_confusions, summarize, sync_gt
from benchmark.retrieval.embedder import (
    FIREWORKS_DEFAULT_MODEL,
    SBERT_DEFAULT_MODEL,
    make_embedder,
)
from benchmark.retrieval.store import KNNStore
from benchmark.runner import run_all

app = typer.Typer(add_completion=False, help="Hawa LLM benchmarking harness")
console = Console()


def _setup_logging() -> None:
    """Route logs through rich so they coexist cleanly with progress bars.

    `show_path=False` strips file:line noise; `markup=True` lets log calls use
    rich markup if they want; `rich_tracebacks=True` makes exceptions readable.
    """
    logging.basicConfig(
        level=logging.INFO,
        format="%(message)s",
        datefmt="[%X]",
        force=True,  # override any handlers attached at import time by deps
        handlers=[
            RichHandler(
                console=console,
                show_path=False,
                markup=True,
                rich_tracebacks=True,
            )
        ],
    )


@app.command("import-csv")
def import_csv_cmd(
    csv: Path = typer.Argument(..., help="Source CSV with team labels"),
    out: Path = typer.Option(Path("data/control.jsonl"), help="Output JSONL"),
    col_text: str = typer.Option("post_text", help="CSV column for the post text"),
    col_score: str = typer.Option("score", help="CSV column for the sentiment score"),
    col_emotion: str = typer.Option("emotion", help="CSV column for the emotion label"),
    col_aspect: str = typer.Option("aspect", help="CSV column for the aspect label"),
    col_is_relevant: str = typer.Option("", help="Optional CSV column for relevance"),
    col_post_id: str = typer.Option("", help="Optional CSV column for post_id (defaults to row number)"),
    skip_invalid: bool = typer.Option(False, help="Drop rows that would fail validation instead of writing them"),
) -> None:
    """Convert a labeled CSV to the benchmark's JSONL format."""
    _setup_logging()
    columns = ColumnMap(
        text=col_text,
        score=col_score,
        emotion=col_emotion,
        aspect=col_aspect,
        is_relevant=col_is_relevant or None,
        post_id=col_post_id or None,
    )
    report = import_csv_to_jsonl(csv, out, columns=columns, skip_invalid=skip_invalid)

    table = Table(title="import summary")
    table.add_column("metric"); table.add_column("value")
    table.add_row("rows written", str(report.n_rows))
    table.add_row("rows skipped (empty text)", str(report.n_skipped_empty))
    table.add_row("rows with invalid score", str(report.n_invalid_score))
    table.add_row("rows with invalid emotion", str(report.n_invalid_emotion))
    table.add_row("rows with invalid aspect", str(report.n_invalid_aspect))
    table.add_row("output", str(report.out_path))
    console.print(table)

    if report.invalid_emotion_values:
        console.print(f"[yellow]invalid emotion values:[/yellow] {dict(report.invalid_emotion_values)}")
    if report.invalid_aspect_values:
        console.print(f"[yellow]invalid aspect values:[/yellow] {dict(report.invalid_aspect_values)}")
    if report.n_invalid_score + report.n_invalid_emotion + report.n_invalid_aspect > 0 and not skip_invalid:
        console.print("[yellow]invalid rows were written; the loader will reject them with line numbers[/yellow]")


@app.command()
def embed(
    data: Path = typer.Option(Path("data/control.jsonl"), help="JSONL dataset"),
    out: Path = typer.Option(Path("data/embeddings.npz"), help="Output .npz"),
    provider: str = typer.Option(
        "sbert",
        help="Embedder provider: 'sbert' (local sentence-transformers) or "
             "'fireworks' (Fireworks AI /v1/embeddings).",
    ),
    model: str = typer.Option(
        "",
        help="Embedding model slug. Defaults: sbert → "
             f"{SBERT_DEFAULT_MODEL}, fireworks → {FIREWORKS_DEFAULT_MODEL}.",
    ),
    k_folds: int = typer.Option(5, help="Number of folds (used downstream)"),
) -> None:
    """Embed every sample in the dataset and write a single .npz file."""
    _setup_logging()
    samples = load_dataset(data)
    samples = assign_folds(samples, k=k_folds)
    embedder = make_embedder(provider, model or None)
    store = KNNStore.build(samples, embedder)
    store.save(out)
    console.print(
        f"[green]embedded[/green] {len(samples)} samples → {out} "
        f"(provider={provider}, model={embedder.name}, "
        f"dim={store.embeddings.shape[1]}, dataset_hash={dataset_hash(samples)})"
    )


@app.command()
def run(
    config: Path = typer.Argument(..., help="Experiment YAML"),
    data: Path = typer.Option(Path("data/control.jsonl")),
    embeddings: Path = typer.Option(Path("data/embeddings.npz")),
    cache: Path = typer.Option(Path("cache.db")),
    results: Path = typer.Option(Path("results")),
    k_folds: int = typer.Option(5),
) -> None:
    """Run all experiments in the config."""
    _setup_logging()
    outputs = asyncio.run(
        run_all(
            config,
            data_path=data,
            embeddings_path=embeddings,
            cache_path=cache,
            results_dir=results,
            k_folds=k_folds,
            console=console,
        )
    )
    table = Table(title="experiments")
    table.add_column("id"); table.add_column("output")
    for exp_id, path in outputs.items():
        table.add_row(exp_id, path)
    console.print(table)


@app.command("sync-gt")
def sync_gt_cmd(
    results: Path = typer.Option(Path("results"), help="Results directory"),
    data: Path = typer.Option(Path("data/control.jsonl"), help="Source dataset"),
) -> None:
    """Refresh GT columns on every parquet in `results/` from `data`.

    Use this after relabeling the dataset (relevance flags or score
    corrections) when you don't want to re-run the experiments. The
    sqlite cache already holds the model predictions; this command just
    re-attaches the current ground truth so `benchmark report` grades
    against fresh labels.
    """
    _setup_logging()
    updates = sync_gt(results, data)
    table = Table(title="parquets synced")
    table.add_column("experiment_id"); table.add_column("gt cells updated")
    for eid, n in updates.items():
        table.add_row(eid, str(n))
    console.print(table)


@app.command()
def report(
    results: Path = typer.Option(Path("results"), help="Results directory"),
    plots: bool = typer.Option(True, help="Render confusion-matrix plots"),
) -> None:
    """Aggregate per-experiment parquets into a summary table + plots."""
    _setup_logging()
    summary = summarize(results)
    if plots:
        plot_all_confusions(results)

    table = Table(title="summary")
    for col in [
        "experiment_id", "n", "n_predicted", "coverage",
        "score_mae", "score_mae_intersection",
        "emotion_macro_f1", "aspect_macro_f1",
        "rel_acc", "rel_recall_irr",
        "latency_ms_p50",
    ]:
        table.add_column(col)
    for _, row in summary.iterrows():
        table.add_row(
            str(row["experiment_id"]),
            str(row["n"]),
            str(row["n_predicted"]),
            _fmt(row.get("coverage", float("nan"))),
            _fmt(row["score_mae"]),
            _fmt(row.get("score_mae_intersection", float("nan"))),
            _fmt(row["emotion_macro_f1"]),
            _fmt(row["aspect_macro_f1"]),
            _fmt(row.get("relevance_accuracy", float("nan"))),
            _fmt(row.get("relevance_recall_irrelevant", float("nan"))),
            _fmt(row["latency_ms_p50"], digits=0),
        )
    console.print(table)
    console.print(f"[green]wrote[/green] {results / 'summary.csv'}")


def _fmt(v: float, digits: int = 3) -> str:
    if v != v:  # NaN
        return "—"
    return f"{v:.{digits}f}"


if __name__ == "__main__":
    app()
