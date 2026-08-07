package com.nebulamind.service;

import com.nebulamind.config.MinIOConfig;
import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.Bucket;
import io.minio.CopySource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Slf4j
@Service("minioStorageService")
@RequiredArgsConstructor
public class MinIOService implements StorageService {

    private final MinioClient minioClient;
    private final MinIOConfig minIOConfig;

    public void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(minIOConfig.getBucketName())
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(minIOConfig.getBucketName())
                    .build());
            log.info("Bucket created: {}", minIOConfig.getBucketName());
        }
    }

    public String uploadFile(String objectName, MultipartFile file) throws Exception {
        ensureBucketExists();

        minioClient.putObject(PutObjectArgs.builder()
                .bucket(minIOConfig.getBucketName())
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());

        log.info("File uploaded to MinIO: {}", objectName);
        return objectName;
    }

    public String uploadFile(String objectName, byte[] content, String contentType) throws Exception {
        ensureBucketExists();

        minioClient.putObject(PutObjectArgs.builder()
                .bucket(minIOConfig.getBucketName())
                .object(objectName)
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .contentType(contentType)
                .build());

        log.info("File uploaded to MinIO: {}", objectName);
        return objectName;
    }

    public InputStream downloadFile(String objectName) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(minIOConfig.getBucketName())
                .object(objectName)
                .build());
    }

    public String copyFile(String sourceObjectName, String destObjectName) throws Exception {
        minioClient.copyObject(CopyObjectArgs.builder()
                .bucket(minIOConfig.getBucketName())
                .object(destObjectName)
                .source(CopySource.builder()
                        .bucket(minIOConfig.getBucketName())
                        .object(sourceObjectName)
                        .build())
                .build());
        log.info("File copied from {} to {}", sourceObjectName, destObjectName);
        return destObjectName;
    }

    public void deleteFile(String objectName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(minIOConfig.getBucketName())
                .object(objectName)
                .build());
        log.info("File deleted from MinIO: {}", objectName);
    }

    public boolean fileExists(String objectName) throws Exception {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minIOConfig.getBucketName())
                    .object(objectName)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return false;
            }
            throw e;
        }
    }

    public List<Bucket> listBuckets() throws Exception {
        return minioClient.listBuckets();
    }
}
