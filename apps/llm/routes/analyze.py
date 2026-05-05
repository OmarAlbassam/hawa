from fastapi import APIRouter, Request

from models import (
    AnalyzeRequest,
    AnalyzeResult,
    BatchAnalyzeRequest,
    BatchAnalyzeResponse,
    HealthResponse,
)

router = APIRouter()


@router.get("/health", response_model=HealthResponse)
async def health(request: Request, deep: bool = False) -> HealthResponse:
    """Liveness probe.

    Default (`deep=false`) returns immediately without touching the LLM —
    appropriate for k8s-style liveness checks. `?deep=true` additionally
    calls `models.list()` on the configured backend, which on serverless
    backends like RunPod will cold-start a worker. Use it sparingly.
    """
    analyzer = request.app.state.analyzer
    reachable = await analyzer.llm_client.is_reachable() if deep else None
    return HealthResponse(
        status="ok",
        llm_reachable=reachable,
        provider=analyzer.settings.provider,
        model=analyzer.settings.model,
    )


@router.post("/analyze", response_model=AnalyzeResult)
async def analyze(post: AnalyzeRequest, request: Request) -> AnalyzeResult:
    analyzer = request.app.state.analyzer
    return await analyzer.analyze_post(post)


@router.post("/analyze/batch", response_model=BatchAnalyzeResponse)
async def analyze_batch(
    body: BatchAnalyzeRequest, request: Request
) -> BatchAnalyzeResponse:
    analyzer = request.app.state.analyzer
    return await analyzer.analyze_batch(
        body.posts,
        brand_name=body.brand_name,
        brand_industry=body.brand_industry,
        keywords=body.keywords,
    )
