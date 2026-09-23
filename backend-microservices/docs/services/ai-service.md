# ai-service (Python / FastAPI)

Sidecar suy luận AI, chạy CPU. Không đăng ký Eureka, không có database, không giữ trạng thái ngoài model đã nạp. Java gọi nó qua URL cấu hình (`AI_SERVICE_URL`).

Tài liệu vận hành chi tiết (biến môi trường, model, ngưỡng): [`ai-service/README.md`](../../ai-service/README.md).

## Vai trò

| Ai gọi | Gọi gì | Để làm gì |
|---|---|---|
| media-service | `POST /embed` | Sinh vector 768 chiều cho bài viết (index Milvus) và cho câu truy vấn của feed khám phá |
| moderation-service | `POST /moderate` | Kiểm duyệt văn bản + ảnh/video của bài viết, bình luận, tin nhắn |

## API

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/health` | – | `{"status":"UP"}`; **503** khi model chưa nạp xong |
| POST | `/embed` | `{"text": "..."}` | `{"vector": [768 float], "dimension": 768}` |
| POST | `/moderate` | `{"text": "...", "media": [{"type": "image\|video", "url": "..."}]}` | `{"is_toxic", "reason", "score"?, "label"?, "at_second"?}` |
| POST | `/moderate/image` | multipart `file` | như trên |
| POST | `/moderate/video` | multipart `file` | như trên |

## Nguyên tắc quan trọng: fail closed

Nếu một media **không tải được, không giải mã được, hoặc quét lỗi**, endpoint trả **502**, không bao giờ trả `is_toxic: false`. moderation-service để exception nổ ra → Kafka retry → vào DLT cho người kiểm tra. Bản cũ nuốt lỗi và báo "Clean", nghĩa là chỉ cần MinIO chập chờn là nội dung vi phạm lọt lưới.

## Chống SSRF và giới hạn tài nguyên

- Chỉ tải từ host nằm trong allow-list (mặc định là host của `MINIO_INTERNAL_URL`, thêm qua `ALLOWED_MEDIA_HOSTS`). Host `localhost`/`127.0.0.1`/`devtunnels.ms` được viết lại thành endpoint MinIO nội bộ trước khi kiểm tra.
- Giới hạn kích thước tải về `MAX_DOWNLOAD_BYTES` (mặc định 50 MB) và số khung hình video quét `MAX_VIDEO_FRAMES` (mặc định 30, rải đều toàn clip thay vì 5 khung/giây suốt video).
- File tạm và handle `cv2.VideoCapture` luôn được giải phóng trong `finally`.
- `/moderate/video` khai báo `def` (không phải `async def`) để FastAPI chạy nó trên threadpool, không chặn event loop.

## Model và ngưỡng

| Mục đích | Model | Ngưỡng |
|---|---|---|
| Embedding tiếng Việt | `VoVanPhuc/sup-SimCSE-VietNamese-phobert-base` | – |
| Văn bản độc hại | `tarudesu/ViSoBERT-HSD` (LABEL_1 offensive, LABEL_2 hate) | `TEXT_TOXIC_THRESHOLD` 0.7 |
| Ảnh nhạy cảm | `AdamCodd/vit-base-nsfw-detector` | `NSFW_THRESHOLD` 0.5 |
| Vật nguy hiểm | `google/vit-base-patch16-224` (ImageNet) | `WEAPON_THRESHOLD` 0.4 |

Nhãn ImageNet là danh sách từ đồng nghĩa ngăn bởi dấu phẩy, nên việc so khớp vũ khí tách từng từ đồng nghĩa và so **khớp nguyên từ**. Bản cũ so chuỗi con nên `"maillot, tank suit"` (đồ bơi) bị gắn nhãn "xe tăng". Các nhãn mơ hồ (`holster`, `syringe`, `tank`) đã bị loại khỏi danh sách.

## Vòng đời

Model được nạp trong **luồng nền** lúc khởi động, nên `/health` trả 503 trong lúc chờ thay vì cả tiến trình không phản hồi. Container healthcheck có `start-period` 300 giây. `HF_HOME` được mount thành volume (`huggingface_cache`) để không tải lại ~2 GB mỗi lần dựng lại container.

## Bảo mật

Không có xác thực. Chỉ đặt trong mạng nội bộ (`ai-service:5000` trong compose; cổng 8000 mở ra host chỉ để debug local). Đây là khoản nợ kỹ thuật đã biết: nếu đưa lên môi trường thật cần thêm shared secret hoặc mTLS giữa Java và Python.
