package com.socialnetwork.media_service.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
  String saveFile(MultipartFile file, String folder);

  void deleteFile(String fileUrl);
}
