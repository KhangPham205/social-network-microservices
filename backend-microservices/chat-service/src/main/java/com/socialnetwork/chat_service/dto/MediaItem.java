package com.socialnetwork.chat_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaItem {
  private String url;
  private String type;
  private String name; // Tên file (optional)
}
