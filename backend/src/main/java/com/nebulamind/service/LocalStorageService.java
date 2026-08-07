package com.nebulamind.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Dev profile 本地文件存储服务，替代 MinIO。
 * 写磁盘目录 ./data/files/，仅用于开发调试。
 */
@Slf4j
@Service("localStorageService")
public class LocalStorageService implements StorageService {

    private final Path basePath;

    public LocalStorageService(@Value("${nebulamind.storage.local.base-path:./data/files}") String basePathStr) {
        this.basePath = Paths.get(basePathStr).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.basePath);
            log.info("LocalStorageService initialized, base path: {}", this.basePath);
        } catch (IOException e) {
            log.warn("Cannot create local storage directory (will use MinIO in production): {}", this.basePath);
        }
    }

    public void ensureBucketExists() {
        // No-op: local filesystem doesn't need bucket creation
    }

    public String uploadFile(String objectName, MultipartFile file) throws IOException {
        ensureBucketExists();
        Path target = resolveSecure(objectName);
        Files.createDirectories(target.getParent());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        log.debug("File saved locally: {}", target);
        return objectName;
    }

    public String uploadFile(String objectName, byte[] content, String contentType) throws IOException {
        ensureBucketExists();
        Path target = resolveSecure(objectName);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        log.debug("File saved locally: {} ({} bytes)", target, content.length);
        return objectName;
    }

    public InputStream downloadFile(String objectName) throws IOException {
        Path source = resolveSecure(objectName);
        if (!Files.exists(source)) {
            throw new IOException("File not found: " + objectName);
        }
        return new ByteArrayInputStream(Files.readAllBytes(source));
    }

    public String copyFile(String sourceObjectName, String destObjectName) throws IOException {
        Path source = resolveSecure(sourceObjectName);
        Path dest = resolveSecure(destObjectName);
        Files.createDirectories(dest.getParent());
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        log.debug("File copied locally: {} -> {}", sourceObjectName, destObjectName);
        return destObjectName;
    }

    public void deleteFile(String objectName) throws IOException {
        Path target = resolveSecure(objectName);
        if (Files.exists(target)) {
            Files.delete(target);
            log.debug("File deleted locally: {}", objectName);
        }
    }

    public boolean fileExists(String objectName) {
        return Files.exists(resolveSecure(objectName));
    }

    /**
     * 防路径穿越：确保目标路径在 basePath 之内
     */
    private Path resolveSecure(String objectName) {
        Path resolved = basePath.resolve(objectName).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new SecurityException("Path traversal attempt: " + objectName);
        }
        return resolved;
    }
}
