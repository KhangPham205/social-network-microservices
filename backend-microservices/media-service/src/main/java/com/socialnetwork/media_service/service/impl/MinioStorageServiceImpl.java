package com.socialnetwork.media_service.service.impl;

import com.socialnetwork.common.exception.BadRequestException;
import com.socialnetwork.media_service.config.MinioProperties;
import com.socialnetwork.media_service.service.StorageService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import java.io.InputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * MinIO-backed media storage. The bucket is provisioned once the context is up so the service
 * still starts when object storage is down.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageServiceImpl implements StorageService {

  private static final String PUBLIC_READ_POLICY =
      """
      {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},\
      "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}""";

  private final MinioClient minioClient;
  private final MinioProperties properties;

  @EventListener(ApplicationReadyEvent.class)
  public void initBucket() {
    String bucket = properties.bucketName();
    try {
      if (minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
        return;
      }
      minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
      minioClient.setBucketPolicy(
          SetBucketPolicyArgs.builder()
              .bucket(bucket)
              .config(PUBLIC_READ_POLICY.formatted(bucket))
              .build());
      log.info("Bucket '{}' created", bucket);
    } catch (Exception e) {
      log.warn("Could not initialise MinIO bucket '{}': {}", bucket, e.toString());
    }
  }

  @Override
  public String saveFile(MultipartFile file, String folder) {
    String bucket = properties.bucketName();
    String objectName = folder + "/" + UUID.randomUUID() + extensionOf(file.getOriginalFilename());
    try (InputStream inputStream = file.getInputStream()) {
      minioClient.putObject(
          PutObjectArgs.builder()
              .bucket(bucket)
              .object(objectName)
              .stream(inputStream, file.getSize(), -1)
              .contentType(file.getContentType())
              .build());
    } catch (Exception e) {
      log.error("Failed to upload {} to MinIO", objectName, e);
      throw new BadRequestException("Failed to upload media file");
    }
    return properties.publicUrl() + "/" + bucket + "/" + objectName;
  }

  @Override
  public void deleteFile(String fileUrl) {
    String objectName = objectNameOf(fileUrl);
    if (objectName == null) {
      log.warn("Ignoring delete request for a URL outside this bucket");
      return;
    }
    try {
      minioClient.removeObject(
          RemoveObjectArgs.builder().bucket(properties.bucketName()).object(objectName).build());
      log.debug("Deleted object {}", objectName);
    } catch (Exception e) {
      log.error("Failed to delete {} from MinIO", objectName, e);
    }
  }

  /** Object key inside the configured bucket, or {@code null} when the URL points elsewhere. */
  private String objectNameOf(String fileUrl) {
    if (fileUrl == null) {
      return null;
    }
    String marker = "/" + properties.bucketName() + "/";
    int index = fileUrl.indexOf(marker);
    if (index < 0) {
      return null;
    }
    String objectName = fileUrl.substring(index + marker.length());
    return objectName.isBlank() ? null : objectName;
  }

  private static String extensionOf(String originalFilename) {
    if (originalFilename == null) {
      return "";
    }
    int dot = originalFilename.lastIndexOf('.');
    return dot != -1 ? originalFilename.substring(dot) : "";
  }
}
