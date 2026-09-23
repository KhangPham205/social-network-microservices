# ai-service

Python sidecar that gives the Java services two capabilities they cannot do themselves: Vietnamese
sentence embeddings for the recommendation feed, and content moderation for text, images and video.

It is a plain HTTP service: it does not register with Eureka, owns no database and holds no state
beyond the loaded models.

## Endpoints

| Method | Path | Request | Response | Caller |
|---|---|---|---|---|
| GET | `/health` | – | `{"status":"UP"}`, or **503** while the models load | container healthcheck |
| POST | `/embed` | `{"text": "..."}` | `{"vector": [768 floats], "dimension": 768}` | media-service (explore feed, post indexing) |
| POST | `/moderate` | `{"text": "...", "media": [{"type":"image","url":"..."}]}` | `{"is_toxic": bool, "reason": str, "score": float?, "label": str?, "at_second": float?}` | moderation-service |
| POST | `/moderate/image` | multipart `file` | same shape as `/moderate` | manual tooling |
| POST | `/moderate/video` | multipart `file` | same shape as `/moderate` | manual tooling |

**Fail closed**: when a media item cannot be downloaded, decoded or scanned, the endpoint answers
`502` rather than reporting clean content. moderation-service lets that propagate so Kafka retries
and, after the retries, records it in the dead-letter topic for human review. Never change this to
return `is_toxic: false` on error — that silently disables moderation.

## Models

| Purpose | Model | Threshold |
|---|---|---|
| Sentence embedding (768-d) | `VoVanPhuc/sup-SimCSE-VietNamese-phobert-base` | – |
| Vietnamese hate speech / offensive text | `tarudesu/ViSoBERT-HSD` (`LABEL_1` offensive, `LABEL_2` hate) | `TEXT_TOXIC_THRESHOLD` = 0.7 |
| NSFW image detection | `AdamCodd/vit-base-nsfw-detector` | `NSFW_THRESHOLD` = 0.5 |
| Dangerous objects (ImageNet) | `google/vit-base-patch16-224` | `WEAPON_THRESHOLD` = 0.4 |

ImageNet labels are comma-separated synonym lists, so weapon matching compares whole synonyms
(`"maillot, tank suit"` must not be flagged as a tank). Ambiguous classes such as `holster`,
`syringe` and `tank` are deliberately absent from the list.

Models are downloaded from Hugging Face on first start into `HF_HOME`; mount that path as a volume
(`huggingface_cache` in docker-compose) so a container restart does not re-download ~2 GB.

## Configuration

Every setting is an environment variable (pydantic-settings, case-insensitive).

| Variable | Default | Meaning |
|---|---|---|
| `PORT` | `5000` | Listen port inside the container |
| `LOG_LEVEL` | `INFO` | Root log level |
| `MINIO_INTERNAL_URL` | `http://minio:9000` | Where media URLs are rewritten to, and the implicitly allowed host |
| `ALLOWED_MEDIA_HOSTS` | empty | Extra `host:port` values that may be downloaded from |
| `REWRITE_HOST_FRAGMENTS` | `localhost,127.0.0.1,devtunnels.ms` | Hostname fragments rewritten to the internal MinIO endpoint |
| `DOWNLOAD_TIMEOUT_SECONDS` | `15` | Per-request download timeout |
| `MAX_DOWNLOAD_BYTES` | `52428800` | Hard cap on a downloaded media item (50 MB) |
| `MAX_VIDEO_FRAMES` | `30` | Frames sampled across the whole clip |
| `TEXT_TOXIC_THRESHOLD` / `NSFW_THRESHOLD` / `WEAPON_THRESHOLD` | `0.7` / `0.5` / `0.4` | Decision thresholds |
| `EMBEDDING_MODEL`, `TEXT_MODERATION_MODEL`, `OBJECT_MODEL`, `NSFW_MODEL` | see table above | Model ids |

## Security

The service has **no authentication**: keep it on the internal network only (in docker-compose it
is reachable as `ai-service:5000`; port 8000 is published for local debugging). Downloads are
restricted to the allow-listed hosts, so a caller cannot use `/moderate` to probe internal
endpoints, and both the download size and the number of analysed video frames are bounded.

## Running

```bash
# with the rest of the stack
cd ../docker && docker compose up -d ai-service

# standalone, for development
pip install -r requirements.txt
MINIO_INTERNAL_URL=http://localhost:9000 uvicorn main:app --host 0.0.0.0 --port 5000
```

The first start downloads the models and `/health` answers `503` until all four are loaded.
