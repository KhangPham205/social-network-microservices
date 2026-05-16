package com.socialnetwork.moderation_service.enums;

public enum ComplaintStatus {
    PENDING,            // Chờ xử lý
    APPROVED,   // Chấp nhận khiếu nại -> Khôi phục bài
    REJECTED       // Từ chối -> Bài vẫn bị xóa
}
