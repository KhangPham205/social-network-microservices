import sys
import io
import tempfile
import cv2
from fastapi import FastAPI, HTTPException, UploadFile, File
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer
from transformers import pipeline
from PIL import Image
import uvicorn


# Hàm log cưỡng ép in ra màn hình Docker ngay lập tức
def force_log(message):
    print(message, flush=True)


app = FastAPI()

force_log("⏳ Đang khởi động AI Service...")

# --- 1. MODEL TEXT ---
force_log("⏳ 1/3: Loading Text Embedding Model...")
embed_model = SentenceTransformer('VoVanPhuc/sup-SimCSE-VietNamese-phobert-base')

force_log("⏳ 2/3: Loading Text Toxicity Model...")
moderation_pipeline = pipeline("text-classification", model="tarudesu/ViSoBERT-HSD")

# --- 2. MODEL ẢNH (Google ViT chuẩn) ---
force_log("⏳ 3/3: Loading Image Detection Model (Google ViT)...")
object_pipeline = pipeline("image-classification", model="google/vit-base-patch16-224")

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
    text: str


@app.get("/")
def health_check():
    return {"status": "AI Service Running - Model: Google ViT"}


@app.post("/embed")
def create_embedding(request: TextRequest):
    try:
        embedding = embed_model.encode(request.text)
        return {"vector": embedding.tolist(), "dimension": len(embedding)}
    except Exception as e:
        force_log(f"❌ Embed Error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/moderate")
def moderate_text(request: TextRequest):
    return {"is_toxic": False, "reason": "Clean"}


# --- 3. KIỂM DUYỆT ẢNH ---
@app.post("/moderate/image")
def moderate_image(file: UploadFile = File(...)):
    try:
        force_log(f"\n--- 📸 NHẬN ĐƯỢC ẢNH: {file.filename} ---")
        image_data = file.file.read()
        image = Image.open(io.BytesIO(image_data))

        results = object_pipeline(image, top_k=5)

        for res in results:
            label_en = res['label'].lower()
            score = res['score']
            for danger_key, vi_msg in DANGEROUS_OBJECTS.items():
                if danger_key in label_en and score > 0.4:
                    return {
                        "is_toxic": True,
                        "reason": f"Vật nguy hiểm: {vi_msg} ({round(score * 100, 1)}%)",
                        "label": label_en,
                        "score": score
                    }

        return {"is_toxic": False, "reason": "Clean", "label": results[0]['label'], "score": results[0]['score']}
    except Exception as e:
        force_log(f"❌ LỖI XỬ LÝ ẢNH: {e}")
        return {"is_toxic": False, "error": str(e)}


# --- 4. KIỂM DUYỆT VIDEO ---
@app.post("/moderate/video")
async def moderate_video(file: UploadFile = File(...)):
    try:
        force_log(f"\n--- 🎬 NHẬN ĐƯỢC VIDEO: {file.filename} ---")

        # 1. Ghi tạm video vào file hệ thống để OpenCV có thể đọc được
        with tempfile.NamedTemporaryFile(delete=False, suffix=".mp4") as temp_video:
            content = await file.read()
            temp_video.write(content)
            temp_video_path = temp_video.name

        # 2. Mở video bằng OpenCV
        cap = cv2.VideoCapture(temp_video_path)
        fps = cap.get(cv2.CAP_PROP_FPS)

        # Nếu không lấy được FPS, mặc định giả định là 30
        if fps == 0:
            fps = 30

        frame_interval = int(fps)  # Lấy 1 frame mỗi giây (Ví dụ FPS=30 thì cứ 30 frames lấy 1 lần)
        frame_count = 0
        violation_detected = None

        force_log(f"📊 FPS của Video: {fps} | Tần suất quét: Cứ sau {frame_interval} frames (1 giây)")

        # 3. Vòng lặp đọc từng frame hình
        while cap.isOpened():
            ret, frame = cap.read()
            if not ret:
                break  # Hết video

            # Chỉ quét đúng frame theo chu kỳ (mỗi giây) để tiết kiệm thời gian xử lý
            if frame_count % frame_interval == 0:
                current_second = round(frame_count / fps, 1)
                force_log(f"⏱️ Đang quét video ở giây thứ: {current_second}s")

                # Chuyển từ BGR (OpenCV) sang RGB (PIL Image) để nạp vào ViT model
                frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                pil_img = Image.fromarray(frame_rgb)

                # Chạy model quét vật thể nguy hiểm trên frame này
                results = object_pipeline(pil_img, top_k=3)

                for res in results:
                    label_en = res['label'].lower()
                    score = res['score']

                    for danger_key, vi_msg in DANGEROUS_OBJECTS.items():
                        if danger_key in label_en and score > 0.4:  # Ngưỡng tự tin > 40%
                            violation_detected = {
                                "is_toxic": True,
                                "reason": f"Phát hiện vật nguy hiểm: {vi_msg} ở giây thứ {current_second}s",
                                "at_second": current_second,
                                "label": label_en,
                                "score": round(score, 4)
                            }
                            force_log(f"❌ PHÁT HIỆN VI PHẠM TRONG VIDEO tại {current_second}s: {label_en}")
                            break  # Thoát loop check label

                if violation_detected:
                    break  # Phát hiện phát chặn luôn, không cần quét tiếp phần còn lại của video

            frame_count += 1

        cap.release()
        import os
        os.unlink(temp_video_path)  # Xóa file tạm sau khi xử lý xong

        # 4. Trả về kết quả kiểm duyệt
        if violation_detected:
            return violation_detected

        force_log("✅ VIDEO AN TOÀN")
        return {
            "is_toxic": False,
            "reason": "Clean",
            "total_duration_scanned": round(frame_count / fps, 1)
        }

    except Exception as e:
        force_log(f"❌ LỖI XỬ LÝ VIDEO: {e}")
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=5000)