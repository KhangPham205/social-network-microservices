"""AI sidecar for the social network backend.

Two responsibilities:

* ``POST /embed``    - Vietnamese sentence embedding (768-d) used by media-service for the
  Milvus-backed explore feed.
* ``POST /moderate`` - text toxicity plus image/video NSFW and weapon detection, used by
  moderation-service to auto-ban content.

Design rules that the Java side depends on:

* **Fail closed.** If a media item cannot be downloaded, decoded or scanned, the endpoint answers
  ``502`` instead of reporting "clean". moderation-service turns that into a Kafka retry and,
  eventually, a dead-letter record that a human reviews.
* **No SSRF.** Only hosts on the allow-list are fetched; ``localhost`` and dev-tunnel hostnames are
  rewritten to the internal MinIO endpoint first.
* **Bounded work.** Downloads and the number of sampled video frames are capped so one request
  cannot occupy the CPU indefinitely.
"""

from __future__ import annotations

import io
import logging
import os
import tempfile
import threading
from contextlib import asynccontextmanager, contextmanager
from typing import Any, Dict, Iterator, List, Optional
from urllib.parse import urlparse, urlunparse

import cv2
import requests
import uvicorn
from fastapi import FastAPI, File, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError
from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings, SettingsConfigDict

# --------------------------------------------------------------------------- settings


class Settings(BaseSettings):
    """Runtime configuration. Every field can be overridden with an environment variable."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    port: int = 5000
    log_level: str = "INFO"

    # Media fetching
    minio_internal_url: str = "http://minio:9000"
    # Hosts whose media may be downloaded. The MinIO host is added automatically.
    allowed_media_hosts: List[str] = Field(default_factory=list)
    # Hostname fragments that are rewritten to minio_internal_url before fetching.
    rewrite_host_fragments: List[str] = Field(
        default_factory=lambda: ["localhost", "127.0.0.1", "devtunnels.ms"]
    )
    download_timeout_seconds: int = 15
    max_download_bytes: int = 50 * 1024 * 1024

    # Thresholds
    text_toxic_threshold: float = 0.7
    nsfw_threshold: float = 0.5
    weapon_threshold: float = 0.4

    # Video sampling
    max_video_frames: int = 30

    # Models
    embedding_model: str = "VoVanPhuc/sup-SimCSE-VietNamese-phobert-base"
    text_moderation_model: str = "tarudesu/ViSoBERT-HSD"
    object_model: str = "google/vit-base-patch16-224"
    nsfw_model: str = "AdamCodd/vit-base-nsfw-detector"


settings = Settings()

logging.basicConfig(
    level=settings.log_level.upper(),
    format="%(asctime)s %(levelname)-5s %(name)s - %(message)s",
)
log = logging.getLogger("ai-service")


# --------------------------------------------------------------------------- weapon labels

# ImageNet-1k class names are comma-separated synonym lists ("maillot, tank suit"), so matching
# must be done per synonym and with whole-word equality. Substring matching used to flag every
# swimsuit as a tank.
DANGEROUS_OBJECTS: Dict[str, str] = {
    "cleaver": "Dao phay",
    "letter opener": "Dao rọc giấy",
    "paper knife": "Dao rọc giấy",
    "switchblade": "Dao bấm",
    "hatchet": "Rìu tay",
    "axe": "Rìu",
    "sword": "Kiếm",
    "dagger": "Dao găm",
    "revolver": "Súng lục",
    "assault rifle": "Súng trường tấn công",
    "rifle": "Súng trường",
    "shotgun": "Súng săn",
    "guillotine": "Máy chém",
}


def _weapon_for(label: str) -> Optional[str]:
    """Return the Vietnamese warning for an ImageNet label, or ``None`` when it is harmless."""
    for synonym in (part.strip().lower() for part in label.split(",")):
        match = DANGEROUS_OBJECTS.get(synonym)
        if match:
            return match
    return None


# --------------------------------------------------------------------------- model registry


class Models:
    """Lazily loaded model handles. ``ready`` flips to True once all four are in memory."""

    def __init__(self) -> None:
        self.embed = None
        self.text_moderation = None
        self.objects = None
        self.nsfw = None
        self.ready = False

    def load(self) -> None:
        from sentence_transformers import SentenceTransformer
        from transformers import pipeline

        log.info("Loading embedding model %s", settings.embedding_model)
        self.embed = SentenceTransformer(settings.embedding_model)

        log.info("Loading text moderation model %s", settings.text_moderation_model)
        self.text_moderation = pipeline(
            "text-classification", model=settings.text_moderation_model
        )

        log.info("Loading object detection model %s", settings.object_model)
        self.objects = pipeline("image-classification", model=settings.object_model)

        log.info("Loading NSFW model %s", settings.nsfw_model)
        self.nsfw = pipeline("image-classification", model=settings.nsfw_model, top_k=None)

        self.ready = True
        log.info("AI service is ready")


models = Models()


# --------------------------------------------------------------------------- API schema


class TextRequest(BaseModel):
    """Request body of /embed and /moderate."""

    text: Optional[str] = ""
    media: Optional[List[Dict[str, str]]] = Field(default_factory=list)


class EmbeddingResponse(BaseModel):
    vector: List[float]
    dimension: int


class ModerationResponse(BaseModel):
    is_toxic: bool
    reason: str
    score: Optional[float] = None
    label: Optional[str] = None
    at_second: Optional[float] = None


class ScanError(Exception):
    """A media item could not be fetched or analysed; the caller must not treat it as clean."""


# --------------------------------------------------------------------------- media fetching


def _resolve_url(raw_url: str) -> str:
    """Rewrite developer-machine hostnames to the internal MinIO endpoint."""
    parsed = urlparse(raw_url)
    if any(fragment in parsed.netloc for fragment in settings.rewrite_host_fragments):
        internal = urlparse(settings.minio_internal_url)
        parsed = parsed._replace(scheme=internal.scheme, netloc=internal.netloc)
    return urlunparse(parsed)


def _allowed_hosts() -> set[str]:
    hosts = {urlparse(settings.minio_internal_url).netloc}
    hosts.update(h.strip() for h in settings.allowed_media_hosts if h.strip())
    return hosts


def _download(url: str) -> bytes:
    """Download a media object, enforcing the host allow-list and the size cap."""
    resolved = _resolve_url(url)
    netloc = urlparse(resolved).netloc
    if netloc not in _allowed_hosts():
        raise ScanError(f"host '{netloc}' is not an allowed media host")

    try:
        response = requests.get(
            resolved, stream=True, timeout=settings.download_timeout_seconds
        )
    except requests.RequestException as exc:
        raise ScanError(f"download failed: {exc}") from exc

    with response:
        if response.status_code != 200:
            raise ScanError(f"download returned HTTP {response.status_code}")

        buffer = bytearray()
        for chunk in response.iter_content(chunk_size=64 * 1024):
            buffer.extend(chunk)
            if len(buffer) > settings.max_download_bytes:
                raise ScanError(
                    f"media exceeds the {settings.max_download_bytes} byte limit"
                )
        return bytes(buffer)


@contextmanager
def _temp_file(content: bytes, suffix: str) -> Iterator[str]:
    """Write bytes to a temp file and always delete it, even when the scan raises."""
    handle = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
    try:
        handle.write(content)
        handle.close()
        yield handle.name
    finally:
        handle.close()
        try:
            os.unlink(handle.name)
        except OSError:
            log.warning("Could not delete temporary file %s", handle.name)


# --------------------------------------------------------------------------- scanning


def _check_image(image: Image.Image) -> Optional[ModerationResponse]:
    """Return a violation, or ``None`` when the image is clean."""
    for item in models.nsfw(image, top_k=None):
        if item["label"].lower() == "nsfw" and float(item["score"]) > settings.nsfw_threshold:
            score = float(item["score"])
            return ModerationResponse(
                is_toxic=True,
                reason=f"Phát hiện nội dung nhạy cảm ({round(score * 100, 1)}%)",
                label="nsfw",
                score=score,
            )

    for result in models.objects(image, top_k=5):
        score = float(result["score"])
        if score <= settings.weapon_threshold:
            continue
        weapon = _weapon_for(result["label"])
        if weapon:
            return ModerationResponse(
                is_toxic=True,
                reason=f"Vật nguy hiểm: {weapon} ({round(score * 100, 1)}%)",
                label=result["label"],
                score=score,
            )
    return None


def _check_image_bytes(content: bytes) -> Optional[ModerationResponse]:
    try:
        image = Image.open(io.BytesIO(content))
        image.load()
    except (UnidentifiedImageError, OSError, ValueError) as exc:
        raise ScanError(f"image could not be decoded: {exc}") from exc
    return _check_image(image)


def _check_video(path: str) -> Optional[ModerationResponse]:
    """Sample up to ``max_video_frames`` frames spread over the clip and scan each one."""
    capture = cv2.VideoCapture(path)
    try:
        if not capture.isOpened():
            raise ScanError("video could not be opened")

        fps = capture.get(cv2.CAP_PROP_FPS) or 30.0
        total_frames = int(capture.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
        if total_frames <= 0:
            raise ScanError("video has no readable frames")

        step = max(1, total_frames // settings.max_video_frames)
        for index in range(0, total_frames, step):
            capture.set(cv2.CAP_PROP_POS_FRAMES, index)
            ok, frame = capture.read()
            if not ok:
                continue
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            violation = _check_image(Image.fromarray(rgb))
            if violation:
                second = round(index / fps, 2)
                violation.at_second = second
                violation.reason = f"{violation.reason} ở giây {second}s"
                return violation
        return None
    finally:
        capture.release()


def _scan_media_item(item: Dict[str, str]) -> Optional[ModerationResponse]:
    url = item.get("url", "")
    if not url:
        return None
    media_type = item.get("type", "").lower()
    log.info("Scanning %s media", media_type or "unknown")
    content = _download(url)

    if media_type == "image":
        return _check_image_bytes(content)
    if media_type == "video":
        with _temp_file(content, ".mp4") as path:
            return _check_video(path)
    raise ScanError(f"unsupported media type '{media_type}'")


# --------------------------------------------------------------------------- application


@asynccontextmanager
async def _lifespan(_: FastAPI):
    # Load in a background thread so /health can answer 503 while the models warm up instead of
    # the whole process being unreachable for several minutes.
    loader = threading.Thread(target=_load_models_safely, name="model-loader", daemon=True)
    loader.start()
    yield


def _load_models_safely() -> None:
    try:
        models.load()
    except Exception:  # noqa: BLE001 - keep the process alive so /health reports 503
        log.exception("Model loading failed; the service will keep reporting 503")


app = FastAPI(title="Social Network AI Service", version="1.0.0", lifespan=_lifespan)


def _require_models() -> None:
    if not models.ready:
        raise HTTPException(status_code=503, detail="Models are still loading")


@app.get("/health")
def health() -> Dict[str, Any]:
    """Liveness and readiness. 503 until every model is loaded."""
    _require_models()
    return {"status": "UP"}


@app.post("/embed", response_model=EmbeddingResponse)
def create_embedding(request: TextRequest) -> EmbeddingResponse:
    _require_models()
    text = request.text or ""
    try:
        vector = models.embed.encode(text)
    except Exception as exc:  # noqa: BLE001 - surfaced to the caller as 502
        log.exception("Embedding failed")
        raise HTTPException(status_code=502, detail=f"Embedding failed: {exc}") from exc
    values = [float(value) for value in vector.tolist()]
    return EmbeddingResponse(vector=values, dimension=len(values))


@app.post("/moderate", response_model=ModerationResponse)
def moderate_content(request: TextRequest) -> ModerationResponse:
    """Scan text first, then every media item. Any scan failure answers 502 (fail closed)."""
    _require_models()

    if request.text and request.text.strip():
        try:
            result = models.text_moderation(request.text)[0]
        except Exception as exc:  # noqa: BLE001
            log.exception("Text moderation failed")
            raise HTTPException(status_code=502, detail=f"Text scan failed: {exc}") from exc

        label = result["label"].upper()
        score = float(result["score"])
        # ViSoBERT: LABEL_0 clean, LABEL_1 offensive, LABEL_2 hate.
        if label in ("LABEL_1", "LABEL_2") and score > settings.text_toxic_threshold:
            kind = "OFFENSIVE" if label == "LABEL_1" else "HATE"
            log.info("Toxic text detected: %s (%.2f)", kind, score)
            return ModerationResponse(
                is_toxic=True,
                reason=f"Văn bản có tính chất {kind} ({round(score * 100, 1)}%)",
                label=kind,
                score=score,
            )

    for item in request.media or []:
        try:
            violation = _scan_media_item(item)
        except ScanError as exc:
            log.warning("Media scan failed for %s: %s", item.get("url"), exc)
            raise HTTPException(status_code=502, detail=f"Media scan failed: {exc}") from exc
        except Exception as exc:  # noqa: BLE001
            log.exception("Unexpected error scanning %s", item.get("url"))
            raise HTTPException(status_code=502, detail=f"Media scan failed: {exc}") from exc
        if violation:
            return violation

    return ModerationResponse(is_toxic=False, reason="Clean")


@app.post("/moderate/image", response_model=ModerationResponse)
def moderate_image(file: UploadFile = File(...)) -> ModerationResponse:
    """Scan an uploaded image directly (used for manual review tooling)."""
    _require_models()
    try:
        violation = _check_image_bytes(file.file.read())
    except ScanError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    return violation or ModerationResponse(is_toxic=False, reason="Clean", label="safe")


@app.post("/moderate/video", response_model=ModerationResponse)
def moderate_video(file: UploadFile = File(...)) -> ModerationResponse:
    """Scan an uploaded video directly. Declared ``def`` so FastAPI runs it off the event loop."""
    _require_models()
    content = file.file.read()
    if len(content) > settings.max_download_bytes:
        raise HTTPException(status_code=413, detail="Video is too large")
    try:
        with _temp_file(content, ".mp4") as path:
            violation = _check_video(path)
    except ScanError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    return violation or ModerationResponse(is_toxic=False, reason="Clean")


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=settings.port)
