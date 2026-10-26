import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request

from .config import settings
from .predictor import SeverityPredictor
from .schemas import (
    BatchRequest,
    BatchResponse,
    HealthResponse,
    SeverityRequest,
    SeverityResponse,
)

logging.basicConfig(level=logging.INFO)


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.predictor = SeverityPredictor(settings)
    yield


app = FastAPI(
    title="CCRS Severity Service",
    description="Predicts complaint severity (Low / Medium / High / Critical) from the "
    "description and complaint type, using a multilingual sentence embedding + CatBoost.",
    version="1.0.0",
    lifespan=lifespan,
)


def _score(request: Request, reqs: list[SeverityRequest]) -> list[SeverityResponse]:
    predictor: SeverityPredictor = request.app.state.predictor
    preds = predictor.predict([(r.description, r.complaint_type) for r in reqs])
    return [
        SeverityResponse(complaint_id=r.complaint_id, model_version=settings.model_version, **p)
        for r, p in zip(reqs, preds)
    ]


@app.post("/severity", response_model=SeverityResponse, tags=["severity"])
def predict_severity(body: SeverityRequest, request: Request):
    return _score(request, [body])[0]


@app.post("/severity/batch", response_model=BatchResponse, tags=["severity"])
def predict_severity_batch(body: BatchRequest, request: Request):
    if len(body.items) > settings.max_batch_size:
        raise HTTPException(413, f"Batch too large (max {settings.max_batch_size})")
    return BatchResponse(results=_score(request, body.items))


@app.get("/health", response_model=HealthResponse, tags=["ops"])
def health(request: Request):
    if getattr(request.app.state, "predictor", None) is None:
        raise HTTPException(503, "model not loaded")
    return HealthResponse(
        status="UP",
        model_version=settings.model_version,
        critical_threshold=settings.critical_threshold,
        review_threshold=settings.review_threshold,
    )
