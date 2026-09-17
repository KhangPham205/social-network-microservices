package com.socialnetwork.user_service.enums;

public enum AccountStatus {
  WAITING, // chờ xử lý
  PENDING, // mới đăng ký, chưa xác minh email
  ACTIVE, // đã xác minh, có thể đăng nhập
  BLOCKED, // bị khóa
  NOT_AUTHORIZED, // chưa được cấp quyền
  NOT_SOLVED // lỗi kỹ thuật hoặc chưa hoàn tất xử lý
}
