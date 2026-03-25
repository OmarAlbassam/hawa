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
async def health(request: Request) -> HealthResponse:
    analyzer = request.app.state.analyzer
    reachable = await analyzer.llm_client.is_reachable()
    return HealthResponse(
        status="ok",
        vllm_reachable=reachable,
        model=analyzer.settings.vllm_model,
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
