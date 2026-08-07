package com.nebulamind.cloud;

import com.nebulamind.dto.FileEventDTO;
import com.nebulamind.entity.File;
import com.nebulamind.entity.User;
import com.nebulamind.repository.FileRepository;
import com.nebulamind.repository.UserRepository;
import com.nebulamind.service.MinIOService;
import com.nebulamind.util.FileTypeDetector;
import com.nebulamind.service.RabbitMQMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@Profile("!dev")
@RequiredArgsConstructor
public class CloudDriveSyncService {

    private final CloudDriveClient cloudDriveClient;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final RabbitMQMessageService rabbitMQMessageService;
    private final MinIOService minIOService;

    private final Map<UUID, LocalDateTime> lastSyncTime = new ConcurrentHashMap<>();
    private static final int MAX_PAGE_SIZE = 100;

    @Transactional
    public void syncFiles(UUID userId) throws IOException {
        LocalDateTime lastSync = lastSyncTime.getOrDefault(userId, LocalDateTime.now().minusDays(1));

        List<CloudDriveFile> allCloudFiles = fetchAllCloudFiles(userId);

        for (CloudDriveFile cloudFile : allCloudFiles) {
            if (cloudFile.getIsFolder()) {
                continue;
            }

            if (cloudFile.getUpdatedAt() == null || cloudFile.getUpdatedAt().isAfter(lastSync)) {
                syncFile(userId, cloudFile);
            }
        }

        lastSyncTime.put(userId, LocalDateTime.now());
        log.info("File sync completed for user: {}", userId);
    }

    private List<CloudDriveFile> fetchAllCloudFiles(UUID userId) throws IOException {
        List<CloudDriveFile> allFiles = new ArrayList<>();
        int page = 1;

        while (true) {
            List<CloudDriveFile> pageFiles = cloudDriveClient.listFiles(userId, null, page, MAX_PAGE_SIZE);
            if (pageFiles == null || pageFiles.isEmpty()) {
                break;
            }
            allFiles.addAll(pageFiles);
            if (pageFiles.size() < MAX_PAGE_SIZE) {
                break;
            }
            page++;
        }

        return allFiles;
    }

    @Transactional
    public void syncFile(UUID userId, CloudDriveFile cloudFile) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        File existingFile = fileRepository.findByCloudDriveFileId(cloudFile.getId())
                .filter(f -> f.getUser().getId().equals(userId))
                .orElse(null);

        if (existingFile != null) {
            if (!existingFile.getHash().equals(cloudFile.getHash())) {
                updateFileFromCloud(userId, existingFile, cloudFile);
            }
            return;
        }

        InputStream inputStream = cloudDriveClient.downloadFile(userId, cloudFile.getId());
        byte[] content = inputStream.readAllBytes();
        String hash = calculateHash(content);

        String objectName = generateObjectName(userId, cloudFile.getName());
        try {
            minIOService.uploadFile(objectName, content, cloudFile.getMimeType());
        } catch (Exception e) {
            log.error("Failed to upload file to MinIO", e);
            throw new RuntimeException("File upload failed", e);
        }

        File file = File.builder()
                .name(cloudFile.getName())
                .path(objectName)
                .cloudDriveFileId(cloudFile.getId())
                .size(cloudFile.getSize())
                .mimeType(cloudFile.getMimeType())
                .fileType(determineFileType(cloudFile.getMimeType(), cloudFile.getName()))
                .hash(hash)
                .user(user)
                .status(File.FileStatus.COMPLETED)
                .aiStatus(File.AiStatus.PENDING)
                .sensitiveLevel(File.SensitiveLevel.NORMAL)
                .isEncrypted(false)
                .version(1)
                .build();

        file = fileRepository.save(file);
        log.info("Synced new file from cloud drive: {} -> {}", cloudFile.getId(), file.getId());

        FileEventDTO event = FileEventDTO.ofUpload(file.getId(), file.getPath(), userId);
        rabbitMQMessageService.sendFileUploadEvent(event);
    }

    @Transactional
    public void updateFileFromCloud(UUID userId, File existingFile, CloudDriveFile cloudFile) throws IOException {
        InputStream inputStream = cloudDriveClient.downloadFile(userId, cloudFile.getId());
        byte[] content = inputStream.readAllBytes();
        String hash = calculateHash(content);

        String oldPath = existingFile.getPath();
        String newPath = generateObjectName(userId, cloudFile.getName());
        try {
            minIOService.uploadFile(newPath, content, cloudFile.getMimeType());
            minIOService.deleteFile(oldPath);
        } catch (Exception e) {
            log.error("Failed to update file in MinIO", e);
            throw new RuntimeException("File update failed", e);
        }

        existingFile.setName(cloudFile.getName());
        existingFile.setPath(newPath);
        existingFile.setSize(cloudFile.getSize());
        existingFile.setMimeType(cloudFile.getMimeType());
        existingFile.setFileType(determineFileType(cloudFile.getMimeType(), cloudFile.getName()));
        existingFile.setHash(hash);
        existingFile.setVersion(existingFile.getVersion() + 1);

        existingFile = fileRepository.save(existingFile);
        log.info("Updated file from cloud drive: {}", existingFile.getId());

        FileEventDTO event = FileEventDTO.ofUpload(existingFile.getId(), existingFile.getPath(), userId);
        rabbitMQMessageService.sendFileUploadEvent(event);
    }

    @Transactional
    public void syncDeletedFiles(UUID userId) throws IOException {
        List<CloudDriveFile> cloudFiles = fetchAllCloudFiles(userId);
        Map<String, CloudDriveFile> cloudFileMap = cloudFiles.stream()
                .filter(f -> !f.getIsFolder())
                .collect(Collectors.toMap(CloudDriveFile::getId, f -> f));

        List<File> localFiles = fileRepository.findByUserId(userId, org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();

        for (File localFile : localFiles) {
            if (localFile.getCloudDriveFileId() != null && !cloudFileMap.containsKey(localFile.getCloudDriveFileId())) {
                try {
                    minIOService.deleteFile(localFile.getPath());
                } catch (Exception e) {
                    log.warn("Failed to delete file from MinIO: {}", localFile.getPath());
                }

                fileRepository.delete(localFile);
                log.info("Deleted local file (not in cloud drive): {}", localFile.getId());

                FileEventDTO event = FileEventDTO.ofDelete(localFile.getId(), userId);
                rabbitMQMessageService.sendFileDeleteEvent(event);
            }
        }
    }

    @Scheduled(fixedRate = 3600000)
    public void scheduledSync() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            try {
                Object token = cloudDriveClient.getToken(user.getId());
                if (token == null) {
                    continue;
                }
                syncFiles(user.getId());
                syncDeletedFiles(user.getId());
            } catch (Exception e) {
                log.error("Failed to sync files for user: {}", user.getId(), e);
            }
        }
    }

    private String calculateHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content);
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    private String generateObjectName(UUID userId, String fileName) {
        return String.format("%s/%s_%s", userId, UUID.randomUUID(), fileName);
    }

    private String determineFileType(String mimeType, String fileName) {
        return FileTypeDetector.detect(mimeType, fileName);
    }
}
