package com.socialnetwork.media_service.service.impl;

import com.socialnetwork.media_service.service.StorageService;
import io.minio.*;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageServiceImpl implements StorageService {

  private final MinioClient minioClient;

  @Value("${minio.bucket-name}")
  private String bucketName;

  @Value("${minio.url}")
  private String minioUrl;

  @Value("${app.minio.public-url}")
  private String publicUrl;

  @PostConstruct
  public void initBucket() {
    try {
      boolean found =
          minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
      if (!found) {
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        String policy =
            "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::"
                + bucketName
                + "/*\"]}]}";
        minioClient.setBucketPolicy(
            SetBucketPolicyArgs.builder().bucket(bucketName).config(policy).build());
        log.info("Bucket '{}' created successfully.", bucketName);
      }
    } catch (Exception e) {
      log.error("Error initializing Minio bucket", e);
    }
  }

  @Override
  public String saveFile(MultipartFile file, String folder) {
    try {
      String originalFilename = file.getOriginalFilename();
      String extension =
          originalFilename != null
              ? originalFilename.substring(originalFilename.lastIndexOf("."))
              : "";
      // Đổi tên file thành UUID để không bị trùng
      String fileName = folder + "/" + UUID.randomUUID() + extension;

      InputStream inputStream = file.getInputStream();
      minioClient.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(fileName).stream(
                  inputStream, file.getSize(), -1)
              .contentType(file.getContentType())
              .build());

      return publicUrl + "/" + bucketName + "/" + fileName;

    } catch (Exception e) {
      log.error("Failed to upload file to Minio", e);
      throw new RuntimeException("Failed to upload media file");
    }
  }

  @Override
  public void deleteFile(String fileUrl) {
    try {
      String objectName =
          fileUrl.substring(fileUrl.indexOf(bucketName + "/") + bucketName.length() + 1);

      minioClient.removeObject(
          RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());

      log.info("Deleted file: {}", objectName);
    } catch (Exception e) {
      log.error("Failed to delete file from Minio: {}", fileUrl, e);
    }
  }
}
