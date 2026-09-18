package com.socialnetwork.moderation_service.enums;

public enum ReportStatus {
  PENDING, // Đang chờ xử lý (Mod chưa xem)
  APPROVED, // Đã duyệt (Mod xác nhận vi phạm)
  REJECTED, // Đã từ chối (Mod thấy không vi phạm)
}
