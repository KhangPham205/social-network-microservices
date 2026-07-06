import sys
import io
import tempfile
import cv2
import os
import requests
from fastapi import FastAPI, HTTPException, UploadFile, File
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer
from transformers import pipeline
from PIL import Image
import uvicorn
from typing import List, Dict, Optional

# Hàm log cưỡng ép in ra màn hình Docker ngay lập tức
def force_log(message):
    print(message, flush=True)

app = FastAPI()

force_log("⏳ Đang khởi động AI Service...")

# --- 1. MODEL TEXT ---
force_log("⏳ 1/4: Loading Text Embedding Model...")
embed_model = SentenceTransformer('VoVanPhuc/sup-SimCSE-VietNamese-phobert-base')

force_log("⏳ 2/4: Loading Text Toxicity Model...")
moderation_pipeline = pipeline("text-classification", model="tarudesu/ViSoBERT-HSD")

# --- 2. MODEL ẢNH (Vũ khí & Vật thể) ---
force_log("⏳ 3/4: Loading Image Detection Model (Google ViT)...")
object_pipeline = pipeline("image-classification", model="google/vit-base-patch16-224")

# --- 3. MODEL NSFW (Khiêu dâm) ---
force_log("⏳ 4/4: Loading NSFW Detection Model...")
nsfw_pipeline = pipeline(
    "image-classification",
    model="AdamCodd/vit-base-nsfw-detector",
    top_k=None  # Bắt buộc trả về toàn bộ danh sách nhãn kèm score
)

force_log("✅ AI SERVICE ĐÃ SẴN SÀNG NHẬN REQUEST!")

# Danh sách mapping từ nhãn tiếng Anh (ImageNet) sang cảnh báo tiếng Việt
DANGEROUS_OBJECTS = {
    "cleaver": "Dao phay/Dao bầu",
    "letter opener": "Dao rọc giấy/Vật sắc nhọn",
    "knife": "Dao",
    "switchblade": "Dao bấm",
    "hatchet": "Rìu tay",
    "axe": "Rìu",
    "sword": "Kiếm",
    "dagger": "Dao găm",
    "revolver": "Súng lục",
    "assault rifle": "Súng trường tấn công",
    "rifle": "Súng trường",
    "shotgun": "Súng săn",
    "holster": "Bao súng (nghi vấn vũ khí)",
    "tank": "Xe tăng/Vũ khí quân sự",
    "projectile": "Đạn dược",
    "syringe": "Kim tiêm",
    "guillotine": "Máy chém"
}

class TextRequest(BaseModel):
    text: Optional[str] = ""
    media: Optional[List[Dict[str, str]]] = []

@app.get("/")
def health_check():
    return {"status": "AI Service Running - Models: ViT (Objects) & AdamCodd (NSFW)"}

@app.post("/embed")
def create_embedding(request: TextRequest):
    try:
        embedding = embed_model.encode(request.text)
        return {"vector": embedding.tolist(), "dimension": len(embedding)}
    except Exception as e:
        force_log(f"❌ Embed Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

def check_image(image: Image.Image):
    nsfw_results = nsfw_pipeline(image, top_k=None)
    for item in nsfw_results:
        label = item["label"].lower()
        score = float(item["score"])
        if label == "nsfw" and score > 0.5:
            force_log(f"❌ Phát hiện hình ảnh NHẠY CẢM/KHIÊU DÂM: {round(score * 100, 1)}%")
            return {"is_toxic": True, "reason": f"Phát hiện nội dung nhạy cảm/khiêu dâm ({round(score * 100, 1)}%)", "label": "nsfw", "score": score}

    results = object_pipeline(image, top_k=5)
    for res in results:
        label_en = res['label'].lower()
        score = res['score']
        for danger_key, vi_msg in DANGEROUS_OBJECTS.items():
            if danger_key in label_en and score > 0.4:
                force_log(f"❌ Phát hiện VŨ KHÍ: {vi_msg}")
                return {"is_toxic": True, "reason": f"Vật nguy hiểm: {vi_msg} ({round(score * 100, 1)}%)", "label": label_en, "score": score}
    return None

def check_video(video_path: str):
    cap = cv2.VideoCapture(video_path)
    fps = cap.get(cv2.CAP_PROP_FPS)
    if fps == 0: fps = 30
    frame_interval = max(1, int(fps / 5))
    frame_count = 0
    violation_detected = None

    while cap.isOpened():
        ret, frame = cap.read()
        if not ret: break

        if frame_count % frame_interval == 0:
            current_second = round(frame_count / fps, 2)
            frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            pil_img = Image.fromarray(frame_rgb)

            res = check_image(pil_img)
            if res and res["is_toxic"]:
                res["reason"] += f" ở giây {current_second}s"
                res["at_second"] = current_second
                violation_detected = res
                break
        frame_count += 1

    cap.release()
    return violation_detected

@app.post("/moderate")
def moderate_content(request: TextRequest):
    # 1. Text moderation
    if request.text and request.text.strip():
        text_res = moderation_pipeline(request.text)[0]
        label = text_res['label'].upper()
        score = float(text_res['score'])
        # ViSoBERT trả về LABEL_0 (CLEAN), LABEL_1 (OFFENSIVE), LABEL_2 (HATE)
        if label in ["LABEL_1", "LABEL_2"] and score > 0.7:
            vi_label = "OFFENSIVE" if label == "LABEL_1" else "HATE"
            force_log(f"❌ Phát hiện văn bản độc hại: {vi_label} ({round(score * 100, 1)}%)")
            return {"is_toxic": True, "reason": f"Văn bản có tính chất {vi_label} ({round(score * 100, 1)}%)", "score": score}

    # 2. Media moderation (URL scanning)
    if request.media:
        for m in request.media:
            m_type = m.get("type", "").lower()
            url = m.get("url", "")
            if not url: continue

            try:
                # Rewrite devtunnels or localhost URL to internal minio docker hostname
                from urllib.parse import urlparse
                parsed = urlparse(url)
                if "devtunnels.ms" in parsed.netloc or "localhost" in parsed.netloc:
                    url = f"http://minio:9000{parsed.path}"

                force_log(f"⏳ Đang tải media để quét: {url}")
                response = requests.get(url, stream=True, timeout=15)
                if response.status_code != 200:
                    force_log(f"⚠️ Lỗi tải URL: {url} (Status: {response.status_code})")
                    continue

                if m_type == "image":
                    image = Image.open(io.BytesIO(response.content))
                    res = check_image(image)
                    if res: return res

                elif m_type == "video":
                    with tempfile.NamedTemporaryFile(delete=False, suffix=".mp4") as temp_video:
                        for chunk in response.iter_content(chunk_size=8192):
                            temp_video.write(chunk)
                        temp_video_path = temp_video.name

                    res = check_video(temp_video_path)
                    os.unlink(temp_video_path)
                    if res: return res

            except Exception as e:
                force_log(f"❌ Lỗi tải/quét media {url}: {e}")

    force_log("✅ TẤT CẢ NỘI DUNG AN TOÀN")
    return {"is_toxic": False, "reason": "Clean"}

# --- Tương thích ngược: KIỂM DUYỆT ẢNH trực tiếp ---
@app.post("/moderate/image")
def moderate_image(file: UploadFile = File(...)):
    try:
        image_data = file.file.read()
        image = Image.open(io.BytesIO(image_data))
        res = check_image(image)
        if res: return res
        return {"is_toxic": False, "reason": "Clean", "label": "safe", "score": 1.0}
    except Exception as e:
        return {"is_toxic": False, "error": str(e)}

# --- Tương thích ngược: KIỂM DUYỆT VIDEO trực tiếp ---
@app.post("/moderate/video")
async def moderate_video(file: UploadFile = File(...)):
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=".mp4") as temp_video:
            content = await file.read()
            temp_video.write(content)
            temp_video_path = temp_video.name
            
        res = check_video(temp_video_path)
        os.unlink(temp_video_path)
        
        if res: return res
        return {"is_toxic": False, "reason": "Clean"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=5000)