package com.socialnetwork.chat_service.enums;

import lombok.Getter;

@Getter
public enum ChatLabel {
  WORK("Công việc", "#0088FF"),
  FAMILY("Gia đình", "#FF3366"),
  FRIENDS("Bạn bè", "#00CC66"),
  CUSTOMER("Khách hàng", "#FF9900"),
  IMPORTANT("Quan trọng", "#FF0000");

  private final String displayName;
  private final String colorCode;

  ChatLabel(String displayName, String colorCode) {
    this.displayName = displayName;
    this.colorCode = colorCode;
  }
}
