package com.nebulamind.service;

import com.nebulamind.config.MinIOConfig;
import com.nebulamind.dto.FileEventDTO;
import com.nebulamind.dto.FileUploadResponse;
import com.nebulamind.entity.File;
import com.nebulamind.entity.User;
import com.nebulamind.exception.ResourceNotFoundException;
import com.nebulamind.repository.FileRepository;
import com.nebulamind.repository.UserRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@Profile("!dev")
@RequiredArgsConstructor
public class FileUploadService {

    private final MinioClient minioClient;
    private final MinIOConfig minIOConfig;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final RabbitMQMessageService rabbitMQMessageService;

    private FileUploadService self;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(FileUploadService self) {
        this.self = self;
    }

    private final Map<String, UploadSession> uploadSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredSessions, 1, 15, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void destroy() {
        cleanupExecutor.shutdown();
    }

    @Scheduled(fixedRate = 900000)
    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        uploadSessions.entrySet().removeIf(entry -> {
            UploadSession session = entry.getValue();
            if (session.getCreatedAt().plusHours(1).isBefore(now)) {
                log.info("Cleaning up expired upload session: {}", entry.getKey());
                cleanupTemp(entry.getKey());
                return true;
            }
            return false;
        });
    }

    public String initUpload(String fileName, String contentType, long fileSize, String fileHash, UUID userId) {
        String uploadId = UUID.randomUUID().toString();
        UploadSession session = new UploadSession();
        session.setFileName(fileName);
        session.setContentType(contentType);
        session.setFileSize(fileSize);
        session.setFileHash(fileHash);
        session.setUserId(userId);
        session.setTotalChunks((int) Math.ceil((double) fileSize / (5 * 1024 * 1024)));
        session.setCreatedAt(LocalDateTime.now());
        uploadSessions.put(uploadId, session);

        log.info("Upload session initialized: {} for file {}", uploadId, fileName);
        return uploadId;
    }

    public FileUploadResponse uploadChunk(String uploadId, int chunkIndex, MultipartFile chunk) throws Exception {
        UploadSession session = uploadSessions.get(uploadId);
        if (session == null) {
            throw new IllegalArgumentException("Invalid upload ID");
        }

        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new IllegalArgumentException("Invalid chunk index");
        }

        if (session.getReceivedChunks().contains(chunkIndex)) {
            log.warn("Chunk {} already received for upload {}", chunkIndex, uploadId);
            return buildResponse(uploadId, chunkIndex, session.getTotalChunks(), false, "Chunk already received");
        }

        String tempChunkPath = saveChunkToTemp(uploadId, chunkIndex, chunk);
        session.getReceivedChunks().add(chunkIndex);
        session.getChunkPaths().put(chunkIndex, tempChunkPath);
        session.setLastUpdatedAt(LocalDateTime.now());

        log.info("Chunk {} received for upload {}, total received: {}", chunkIndex, uploadId, session.getReceivedChunks().size());

        if (session.getReceivedChunks().size() == session.getTotalChunks()) {
            return self.completeUpload(session, uploadId);
        }

        return buildResponse(uploadId, chunkIndex, session.getTotalChunks(), false, "Chunk uploaded");
    }

    private String saveChunkToTemp(String uploadId, int chunkIndex, MultipartFile chunk) throws IOException {
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "nebulamind", uploadId);
        Files.createDirectories(tempDir);
        Path chunkPath = tempDir.resolve(String.format("chunk_%04d", chunkIndex));
        chunk.transferTo(chunkPath.toFile());
        return chunkPath.toString();
    }

    @Transactional
    public FileUploadResponse completeUpload(UploadSession session, String uploadId) throws Exception {
        byte[] mergedContent = mergeChunks(session);
        String storedHash = calculateHash(mergedContent);

        if (!storedHash.equals(session.getFileHash())) {
            cleanupTemp(uploadId);
            throw new IllegalArgumentException("File hash mismatch");
        }

        User user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", session.getUserId().toString()));

        String objectName = generateObjectName(session.getUserId(), session.getFileName());
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(minIOConfig.getBucketName())
                .object(objectName)
                .stream(new java.io.ByteArrayInputStream(mergedContent), mergedContent.length, -1)
                .contentType(session.getContentType())
                .build());

        File file = File.builder()
                .name(session.getFileName())
                .path(objectName)
                .size(session.getFileSize())
                .mimeType(session.getContentType())
                .fileType(determineFileType(session.getContentType()))
                .hash(session.getFileHash())
                .user(user)
                .status(File.FileStatus.COMPLETED)
                .aiStatus(File.AiStatus.PENDING)
                .sensitiveLevel(File.SensitiveLevel.NORMAL)
                .isEncrypted(false)
                .version(1)
                .build();

        file = fileRepository.save(file);
        log.info("File saved to database: {}", file.getId());

        cleanupTemp(uploadId);
        uploadSessions.remove(uploadId);

        FileEventDTO event = FileEventDTO.ofUpload(file.getId(), objectName, session.getUserId());
        rabbitMQMessageService.sendFileUploadEvent(event);
        log.info("File upload event sent for file {}", file.getId());

        return buildResponse(uploadId, session.getTotalChunks() - 1, session.getTotalChunks(), true, "Upload completed");
    }

    private byte[] mergeChunks(UploadSession session) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (int i = 0; i < session.getTotalChunks(); i++) {
            String chunkPath = session.getChunkPaths().get(i);
            if (chunkPath != null) {
                byte[] chunkBytes = Files.readAllBytes(Paths.get(chunkPath));
                outputStream.write(chunkBytes);
            }
        }
        return outputStream.toByteArray();
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

    private String determineFileType(String mimeType) {
        if (mimeType == null) {
            return "unknown";
        }
        String type = mimeType.split("/")[0];
        return switch (type.toLowerCase()) {
            case "application" -> {
                if (mimeType.contains("pdf")) yield "pdf";
                if (mimeType.contains("word")) yield "word";
                if (mimeType.contains("excel") || mimeType.contains("spreadsheet")) yield "excel";
                if (mimeType.contains("powerpoint") || mimeType.contains("presentation")) yield "ppt";
                yield "document";
            }
            case "image" -> "image";
            case "video" -> "video";
            case "audio" -> "audio";
            case "text" -> "text";
            default -> "other";
        };
    }

    private void cleanupTemp(String uploadId) {
        try {
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "nebulamind", uploadId);
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete temp file: {}", path);
                            }
                        });
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup temp directory: {}", uploadId);
        }
    }

    private FileUploadResponse buildResponse(String uploadId, int chunkIndex, int totalChunks, boolean completed, String message) {
        return FileUploadResponse.builder()
                .uploadId(uploadId)
                .chunkIndex(chunkIndex)
                .totalChunks(totalChunks)
                .completed(completed)
                .message(message)
                .build();
    }

    public void cancelUpload(String uploadId) {
        cleanupTemp(uploadId);
        uploadSessions.remove(uploadId);
        log.info("Upload cancelled: {}", uploadId);
    }

    private static class UploadSession {
        private String fileName;
        private String contentType;
        private long fileSize;
        private String fileHash;
        private UUID userId;
        private int totalChunks;
        private LocalDateTime createdAt;
        private LocalDateTime lastUpdatedAt;
        private java.util.Set<Integer> receivedChunks = ConcurrentHashMap.newKeySet();
        private Map<Integer, String> chunkPaths = new ConcurrentHashMap<>();

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        public String getFileHash() { return fileHash; }
        public void setFileHash(String fileHash) { this.fileHash = fileHash; }
        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
        public int getTotalChunks() { return totalChunks; }
        public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getLastUpdatedAt() { return lastUpdatedAt; }
        public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }
        public java.util.Set<Integer> getReceivedChunks() { return receivedChunks; }
        public Map<Integer, String> getChunkPaths() { return chunkPaths; }
    }
}
