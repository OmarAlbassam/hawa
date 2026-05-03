"""Typer CLI: `benchmark embed | run | report`.

Installed as the `benchmark` console script via pyproject.
"""

from __future__ import annotations

import asyncio
import logging
from pathlib import Path

import typer
from rich.console import Console
from rich.table import Table

from benchmark.dataset import assign_folds, dataset_hash, load_dataset
from benchmark.report import plot_all_confusions, summarize
from benchmark.retrieval.embedder import SBERTEmbedder
from benchmark.retrieval.store import KNNStore
from benchmark.runner import run_all

app = typer.Typer(add_completion=False, help="Hawa LLM benchmarking harness")
console = Console()


@app.command()
def embed(
    data: Path = typer.Option(Path("data/control.jsonl"), help="JSONL dataset"),
    out: Path = typer.Option(Path("data/embeddings.npz"), help="Output .npz"),
    model: str = typer.Option("BAAI/bge-small-en-v1.5", help="Sentence-transformers model"),
    k_folds: int = typer.Option(5, help="Number of folds (used downstream)"),
) -> None:
    """Embed every sample in the dataset and write a single .npz file."""
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    samples = load_dataset(data)
    samples = assign_folds(samples, k=k_folds)
    embedder = SBERTEmbedder(name=model)
    store = KNNStore.build(samples, embedder)
    store.save(out)
    console.print(
        f"[green]embedded[/green] {len(samples)} samples → {out} "
        f"(dim={store.embeddings.shape[1]}, dataset_hash={dataset_hash(samples)})"
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
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    outputs = asyncio.run(
        run_all(
            config,
            data_path=data,
            embeddings_path=embeddings,
            cache_path=cache,
            results_dir=results,
            k_folds=k_folds,
        )
    )
    table = Table(title="experiments")
    table.add_column("id"); table.add_column("output")
    for exp_id, path in outputs.items():
        table.add_row(exp_id, path)
    console.print(table)


@app.command()
def report(
    results: Path = typer.Option(Path("results"), help="Results directory"),
    plots: bool = typer.Option(True, help="Render confusion-matrix plots"),
) -> None:
    """Aggregate per-experiment parquets into a summary table + plots."""
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    summary = summarize(results)
    if plots:
        plot_all_confusions(results)

    table = Table(title="summary")
    for col in [
        "experiment_id", "n", "n_predicted",
        "score_mae", "score_rmse",
        "emotion_accuracy", "emotion_macro_f1",
        "aspect_accuracy", "aspect_macro_f1",
        "latency_ms_p50",
    ]:
        table.add_column(col)
    for _, row in summary.iterrows():
        table.add_row(
            str(row["experiment_id"]),
            str(row["n"]),
            str(row["n_predicted"]),
            _fmt(row["score_mae"]),
            _fmt(row["score_rmse"]),
            _fmt(row["emotion_accuracy"]),
            _fmt(row["emotion_macro_f1"]),
            _fmt(row["aspect_accuracy"]),
            _fmt(row["aspect_macro_f1"]),
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
