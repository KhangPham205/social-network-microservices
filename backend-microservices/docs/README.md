# Tài liệu kiến trúc

Đọc theo thứ tự nếu bạn mới vào dự án. Mục tiêu của bộ tài liệu này: **hiểu được hệ thống mà không cần mở code**.

| # | Tài liệu | Trả lời câu hỏi |
|---|---|---|
| 01 | [Kiến trúc tổng quan](01-kien-truc-tong-quan.md) | Hệ thống có những gì? Service nào sở hữu dữ liệu nào? Một request đi qua đâu? |
| 02 | [Module common](02-common-module.md) | Thư viện dùng chung có gì? Dùng exception/security/paging thế nào? |
| 03 | [Bảo mật](03-bao-mat.md) | JWT hoạt động ra sao? Phân quyền ở đâu? Internal API được bảo vệ thế nào? |
| 04 | [Kafka & event](04-kafka-events.md) | Có những topic nào? Payload ra sao? Ai publish, ai consume? |
| 05 | [Cấu hình & triển khai](05-cau-hinh-va-trien-khai.md) | Biến môi trường nào? Chạy local và Docker ra sao? |
| 06 | [Quy ước code](06-quy-uoc-code.md) | Viết code theo chuẩn nào? Checklist trước khi merge? |

## Chi tiết từng service

| Service | Tài liệu |
|---|---|
| api-gateway, discovery-server | [services/api-gateway.md](services/api-gateway.md) |
| auth-service | [services/auth-service.md](services/auth-service.md) |
| user-service | [services/user-service.md](services/user-service.md) |
| media-service | [services/media-service.md](services/media-service.md) |
| chat-service | [services/chat-service.md](services/chat-service.md) |
| notification-service | [services/notification-service.md](services/notification-service.md) |
| moderation-service | [services/moderation-service.md](services/moderation-service.md) |
| ai-service (Python) | [services/ai-service.md](services/ai-service.md) |

## Giữ tài liệu không lạc hậu

Tài liệu chỉ có giá trị khi còn đúng. Quy tắc: **thay đổi contract, endpoint, event hoặc biến môi trường thì cập nhật tài liệu tương ứng trong cùng commit**. Chi tiết trong [06 · Quy ước code](06-quy-uoc-code.md).
