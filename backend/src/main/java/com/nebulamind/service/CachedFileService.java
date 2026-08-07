package com.nebulamind.service;

import com.nebulamind.entity.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Profile("!dev")
@RequiredArgsConstructor
public class CachedFileService {

    private final FileService fileService;
    private final CacheService cacheService;

    @Cacheable(value = "userFiles", key = "#userId")
    public Page<File> getUserFiles(UUID userId, Pageable pageable) {
        log.debug("Cache miss for user files: {}", userId);
        return fileService.getUserFiles(userId, pageable);
    }

    @Cacheable(value = "fileDetails", key = "#id")
    public File getFileById(UUID id, UUID userId) {
        log.debug("Cache miss for file detail: {}", id);
        return fileService.getFileById(id, userId);
    }

    @Transactional
    @CacheEvict(value = {"userFiles", "fileDetails"}, key = "#userId")
    public File createFile(com.nebulamind.dto.FileRequest request, UUID userId) {
        File file = fileService.createFile(request, userId);
        cacheService.evictFileList(userId.toString());
        log.debug("Cache evicted after file creation: {}", file.getId());
        return file;
    }

    @Transactional
    @CacheEvict(value = {"userFiles", "fileDetails"}, key = "#userId")
    public File updateFile(UUID id, com.nebulamind.dto.FileRequest request, UUID userId) {
        File file = fileService.updateFile(id, request, userId);
        cacheService.evictFileList(userId.toString());
        cacheService.evictFileDetail(id.toString());
        log.debug("Cache evicted after file update: {}", id);
        return file;
    }

    @Transactional
    @CacheEvict(value = {"userFiles", "fileDetails"}, key = "#userId")
    public void deleteFile(UUID id, UUID userId) {
        fileService.deleteFile(id, userId);
        cacheService.evictFileList(userId.toString());
        cacheService.evictFileDetail(id.toString());
        log.debug("Cache evicted after file deletion: {}", id);
    }

    public InputStream downloadFile(UUID id, UUID userId) throws Exception {
        return fileService.downloadFile(id, userId);
    }

    @CacheEvict(value = {"userFiles", "fileDetails"}, allEntries = true)
    public void evictAllCaches() {
        log.info("All caches evicted");
    }

    public List<File> findDuplicateFiles(UUID userId, String hash) {
        return fileService.findDuplicateFiles(userId, hash);
    }

    public File processCallback(com.nebulamind.dto.FileProcessCallbackRequest request) {
        File file = fileService.processCallback(request);
        cacheService.evictFileDetail(request.getFileId().toString());
        log.debug("Cache evicted after callback: {}", request.getFileId());
        return file;
    }
}
