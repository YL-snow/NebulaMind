package com.nebulamind.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebulamind.dto.FileEventDTO;
import com.nebulamind.dto.FileProcessCallbackRequest;
import com.nebulamind.entity.File;
import com.nebulamind.entity.FileContent;
import com.nebulamind.entity.User;
import com.nebulamind.exception.ResourceNotFoundException;
import com.nebulamind.repository.FileContentRepository;
import com.nebulamind.repository.FileRepository;
import com.nebulamind.repository.UserRepository;
import com.nebulamind.util.FileTypeDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Slf4j
@Service
public class FileService {

    private final FileRepository fileRepository;
    private final FileContentRepository fileContentRepository;
    private final UserRepository userRepository;
    private final FileVersionService fileVersionService;

    private final StorageService storageService;

    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RabbitMQMessageService rabbitMQMessageService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NoOpMessageService noOpMessageService;

    public FileService(FileRepository fileRepository,
                       FileContentRepository fileContentRepository,
                       UserRepository userRepository,
                       StorageService storageService,
                       FileVersionService fileVersionService,
                       ObjectMapper objectMapper) {
        this.fileRepository = fileRepository;
        this.fileContentRepository = fileContentRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.fileVersionService = fileVersionService;
        this.objectMapper = objectMapper;
    }

    public Page<File> getUserFiles(UUID userId, Pageable pageable) {
        return fileRepository.findByUserId(userId, pageable);
    }

    public File getFileById(UUID id, UUID userId) {
        return fileRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("File", id.toString()));
    }

    @Transactional
    public File createFile(com.nebulamind.dto.FileRequest request, UUID userId) {
        return createFile(request, userId, false);
    }

    @Transactional
    public File createFile(com.nebulamind.dto.FileRequest request, UUID userId, boolean skipProcessing) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        if (request.getContent() == null) {
            throw new IllegalArgumentException("File content is required");
        }

        String hash = calculateFileHash(request.getContent());

        List<File> duplicates = findDuplicateFiles(userId, hash);
        if (!duplicates.isEmpty()) {
            File existingFile = duplicates.get(0);
            log.warn("Duplicate file detected for user {} with hash {}, existing file: {}", userId, hash, existingFile.getId());
            throw new IllegalArgumentException("File already exists with id: " + existingFile.getId());
        }

        String objectName = generateObjectName(userId, request.getName());
        try {
            uploadToStorage(objectName, request.getContent(), request.getMimeType());
        } catch (Exception e) {
            log.error("Failed to upload file to storage", e);
            throw new RuntimeException("File upload failed", e);
        }

        File file = File.builder()
                .name(request.getName())
                .path(objectName)
                .size(request.getSize())
                .mimeType(request.getMimeType())
                .fileType(determineFileType(request.getMimeType(), request.getName()))
                .hash(hash)
                .user(user)
                .status(File.FileStatus.COMPLETED)
                .aiStatus(skipProcessing ? File.AiStatus.COMPLETED : File.AiStatus.PENDING)
                .sensitiveLevel(File.SensitiveLevel.NORMAL)
                .isEncrypted(false)
                .version(1)
                .build();

        file = fileRepository.save(file);
        log.info("File created: {}", file.getId());

        FileEventDTO event = FileEventDTO.ofUpload(file.getId(), file.getPath(), userId);
        if (!skipProcessing) {
            sendAfterCommit(() -> sendUploadEvent(event));
        }

        return file;
    }

    @Transactional
    public File updateFile(UUID id, com.nebulamind.dto.FileRequest request, UUID userId) {
        File file = getFileById(id, userId);

        if (request != null) {
            if (!file.getName().equals(request.getName())) {
                String oldPath = file.getPath();
                String newPath = generateObjectName(userId, request.getName());
                try {
                    copyInStorage(oldPath, newPath);
                    deleteFromStorage(oldPath);
                    file.setPath(newPath);
                } catch (Exception e) {
                    log.error("Failed to update file in MinIO", e);
                    throw new RuntimeException("File update failed", e);
                }
            }

            file.setName(request.getName());

            if (request.getSize() != null) {
                file.setSize(request.getSize());
            }
            if (request.getContent() != null) {
                file.setHash(calculateFileHash(request.getContent()));
                file.setVersion(file.getVersion() + 1);
            }
        }

        return fileRepository.save(file);
    }

    @Transactional
    public void deleteFile(UUID id, UUID userId) {
        File file = getFileById(id, userId);

        try {
            deleteFromStorage(file.getPath());
        } catch (Exception e) {
            log.warn("Failed to delete file from storage: {}", file.getPath(), e);
        }

        fileVersionService.deleteVersionsByFileId(id);

        fileRepository.delete(file);
        log.info("File deleted: {}", id);

        FileEventDTO event = FileEventDTO.ofDelete(id, userId);
        sendAfterCommit(() -> sendDeleteEvent(event));
    }

    public InputStream downloadFile(UUID id, UUID userId) throws Exception {
        File file = getFileById(id, userId);
        return downloadFromStorage(file.getPath());
    }

    @Transactional
    public File saveFile(File file) {
        return fileRepository.save(file);
    }

    public File getFileForDownload(UUID id) {
        return fileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File", id.toString()));
    }

    public List<File> findDuplicateFiles(UUID userId, String hash) {
        return fileRepository.findByHash(hash).stream()
                .filter(f -> f.getUser().getId().equals(userId))
                .toList();
    }

    public Optional<File> findFileByContent(UUID userId, byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        return findDuplicateFiles(userId, calculateFileHash(content)).stream().findFirst();
    }

    @Transactional
    public File processCallback(FileProcessCallbackRequest request) {
        File file = fileRepository.findById(request.getFileId())
                .orElseThrow(() -> new ResourceNotFoundException("File", request.getFileId().toString()));

        try {
            file.setAiStatus(File.AiStatus.valueOf(request.getStatus().toUpperCase()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid AI status: {}, defaulting to PENDING", request.getStatus());
            file.setAiStatus(File.AiStatus.PENDING);
        }

        if (request.getCategory() != null) {
            file.setCategory(request.getCategory());
        }
        if (request.getTags() != null) {
            file.setTags(normalizeTags(request.getTags()));
        }
        if (request.getSummary() != null) {
            File fileForContent = file;
            FileContent content = fileContentRepository.findByFileIdAndChunkIndex(file.getId(), 0)
                    .orElseGet(() -> FileContent.builder()
                            .file(fileForContent)
                            .chunkIndex(0)
                            .build());
            content.setChunkContent(request.getSummary());
            content.setCharCount(request.getSummary().length());
            content.setTokenCount((int) Math.ceil(request.getSummary().length() / 4.0));
            fileContentRepository.save(content);
        }
        if (request.getSensitiveLevel() != null) {
            try {
                file.setSensitiveLevel(File.SensitiveLevel.valueOf(request.getSensitiveLevel().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid sensitive level: {}, defaulting to NORMAL", request.getSensitiveLevel());
                file.setSensitiveLevel(File.SensitiveLevel.NORMAL);
            }
        }

        if ("FAILED".equalsIgnoreCase(request.getStatus())) {
            file.setAiErrorMessage(request.getErrorMessage());
        } else if ("COMPLETED".equalsIgnoreCase(request.getStatus())) {
            file.setAiErrorMessage(null);
        }

        file = fileRepository.save(file);
        log.info("File processing callback received: {} status: {}", file.getId(), request.getStatus());

        return file;
    }

    private String calculateFileHash(byte[] content) {
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

    private void uploadToStorage(String objectName, byte[] content, String mimeType) throws Exception {
        storageService.uploadFile(objectName, content, mimeType);
    }

    private void deleteFromStorage(String objectName) throws Exception {
        storageService.deleteFile(objectName);
    }

    private void copyInStorage(String oldPath, String newPath) throws Exception {
        storageService.copyFile(oldPath, newPath);
    }

    private InputStream downloadFromStorage(String objectName) throws Exception {
        return storageService.downloadFile(objectName);
    }

    // ---- Message delegation methods ----

    private void sendUploadEvent(FileEventDTO event) {
        if (rabbitMQMessageService != null) {
            rabbitMQMessageService.sendFileUploadEvent(event);
        } else if (noOpMessageService != null) {
            noOpMessageService.sendFileUploadEvent(event);
        }
    }

    private void sendDeleteEvent(FileEventDTO event) {
        if (rabbitMQMessageService != null) {
            rabbitMQMessageService.sendFileDeleteEvent(event);
        } else if (noOpMessageService != null) {
            noOpMessageService.sendFileDeleteEvent(event);
        }
    }

    private void sendAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    public String normalizeTags(String rawTags) {
        if (rawTags == null) {
            return null;
        }
        try {
            String trimmed = rawTags.trim();
            if (trimmed.isEmpty()) {
                return "[]";
            }
            List<String> tags;
            if (trimmed.startsWith("[")) {
                tags = objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
            } else {
                tags = Arrays.stream(trimmed.split("[,，]"))
                        .map(String::trim)
                        .filter(tag -> !tag.isEmpty())
                        .toList();
            }
            return objectMapper.writeValueAsString(tags);
        } catch (Exception e) {
            log.warn("Invalid tags value: {}, falling back to empty array", rawTags);
            return "[]";
        }
    }

    private String determineFileType(String mimeType, String fileName) {
        return FileTypeDetector.detect(mimeType, fileName);
    }
}
