package com.nebulamind.service;

import com.nebulamind.entity.File;
import com.nebulamind.entity.FileVersion;
import com.nebulamind.entity.User;
import com.nebulamind.util.FileTypeDetector;
import com.nebulamind.exception.ResourceNotFoundException;
import com.nebulamind.repository.FileRepository;
import com.nebulamind.repository.FileVersionRepository;
import com.nebulamind.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

/**
 * 文件版本管理服务
 *
 * 功能：
 *   - 版本创建：每次文件修改时自动创建新版本快照
 *   - 版本对比：对比两个版本之间的差异（文本diff）
 *   - 版本回滚：恢复到指定历史版本
 *   - 编辑痕迹追踪：记录谁、何时、做了什么
 */
@Slf4j
@Service
public class FileVersionService {

    private final FileVersionRepository fileVersionRepository;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final EncryptionService encryptionService;
    private final KeyManagementService keyManagementService;

    public FileVersionService(FileVersionRepository fileVersionRepository,
                              FileRepository fileRepository,
                              UserRepository userRepository,
                              StorageService storageService,
                              EncryptionService encryptionService,
                              KeyManagementService keyManagementService) {
        this.fileVersionRepository = fileVersionRepository;
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.encryptionService = encryptionService;
        this.keyManagementService = keyManagementService;
    }

    private static final int MAX_TEXT_EDIT_BYTES = 2 * 1024 * 1024;
    private static final Set<String> TEXT_EDITABLE_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "log", "csv", "json", "xml", "yml", "yaml",
            "ini", "conf", "properties", "sql", "html", "htm", "css", "js", "ts",
            "jsx", "tsx", "java", "kt", "py", "c", "cpp", "h", "hpp", "sh",
            "bat", "ps1", "env", "gitignore"
    );

    /**
     * 创建新版本快照
     * 每次文件内容修改时调用，保存当前版本到历史记录
     *
     * @param file      文件实体
     * @param content   新文件内容
     * @param comment   版本备注
     * @param userId    操作者ID
     * @return 创建的版本记录
     */
    @Transactional
    public FileVersion createVersion(File file, byte[] content, String comment, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        long versionCount = fileVersionRepository.countByFileId(file.getId());
        int nextVersion = (int) versionCount + 1;

        String fileHash = calculateHash(content);
        String storagePath = buildVersionPath(file, nextVersion);

        // 保存版本文件到存储
        try {
            uploadToStorage(storagePath, content, file.getMimeType());
        } catch (Exception e) {
            log.error("Failed to upload version file to storage: {}", storagePath, e);
            throw new RuntimeException("版本文件保存失败", e);
        }

        FileVersion version = FileVersion.builder()
                .file(file)
                .versionNumber(nextVersion)
                .fileSize((long) content.length)
                .fileHash(fileHash)
                .storagePath(storagePath)
                .comment(comment != null ? comment : "版本 " + nextVersion)
                .createdBy(user)
                .build();

        version = fileVersionRepository.save(version);

        // 更新文件的版本号
        file.setVersion(nextVersion);
        fileRepository.save(file);

        log.info("Version {} created for file {} (hash: {})", nextVersion, file.getId(), fileHash);
        return version;
    }

    @Transactional
    public File uploadNewVersion(UUID fileId, MultipartFile uploadedFile, String comment, UUID userId) {
        File file = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("File", fileId.toString()));
        if (uploadedFile == null || uploadedFile.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的新版本文件");
        }
        byte[] content;
        try {
            content = uploadedFile.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("读取上传文件失败", e);
        }
        String name = uploadedFile.getOriginalFilename();
        if (name == null || name.isBlank()) {
            name = file.getName();
        }
        String mimeType = uploadedFile.getContentType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = file.getMimeType();
        }
        String fileType = FileTypeDetector.detect(mimeType, name);
        String versionComment = comment == null || comment.isBlank() ? "上传新版本" : comment;
        return applyNewVersion(file, content, name, mimeType, fileType, versionComment, userId, false);
    }

    @Transactional
    public File uploadNewVersion(UUID fileId, MultipartFile uploadedFile, String comment, UUID userId, boolean encrypted) {
        File file = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("File", fileId.toString()));
        if (uploadedFile == null || uploadedFile.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的新版本文件");
        }
        byte[] content;
        try {
            content = uploadedFile.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("读取上传文件失败", e);
        }
        String name = uploadedFile.getOriginalFilename();
        if (name == null || name.isBlank()) {
            name = file.getName();
        }
        String mimeType = uploadedFile.getContentType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = file.getMimeType();
        }
        String fileType = FileTypeDetector.detect(mimeType, name);
        String versionComment = comment == null || comment.isBlank() ? "上传新版本" : comment;
        return applyNewVersion(file, content, name, mimeType, fileType, versionComment, userId, encrypted);
    }

    @Transactional
    public File saveTextVersion(UUID fileId, String content, String comment, UUID userId) {
        File file = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("File", fileId.toString()));
        if (File.EncryptionMode.CLIENT.equals(file.getEncryptionMode())) {
            throw new IllegalArgumentException("端到端加密文件需在浏览器本地解密后编辑，请使用上传新版本功能。");
        }
        if (content == null) {
            throw new IllegalArgumentException("文件内容不能为空");
        }
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_EDIT_BYTES) {
            throw new IllegalArgumentException("文本内容过大，请下载后本地编辑再上传新版本");
        }
        if (!isTextEditable(file)) {
            throw new IllegalArgumentException("该文件类型不支持在线编辑，请下载后在本地修改并上传新版本");
        }
        String versionComment = comment == null || comment.isBlank() ? "在线编辑" : comment;
        return applyNewVersion(file, bytes, file.getName(), file.getMimeType(), file.getFileType(), versionComment, userId, false);
    }

    /**
     * 获取文件的所有版本列表（按版本号降序）
     */
    @Transactional(readOnly = true)
    public List<FileVersion> getVersionHistory(UUID fileId) {
        return fileVersionRepository.findByFileIdOrderByVersionNumberDesc(fileId);
    }

    /**
     * 获取版本历史（仅返回前端所需字段，避免懒加载实体序列化报错）
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getVersionHistoryItems(UUID fileId) {
        return getVersionHistory(fileId).stream()
                .map(this::toVersionItem)
                .collect(Collectors.toList());
    }

    /**
     * 获取特定版本详情（安全 DTO）
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getVersionItem(UUID fileId, int versionNumber) {
        return toVersionItem(getVersion(fileId, versionNumber));
    }

    private Map<String, Object> toVersionItem(FileVersion version) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", version.getId());
        item.put("versionNumber", version.getVersionNumber());
        item.put("fileSize", version.getFileSize());
        item.put("fileHash", version.getFileHash());
        item.put("comment", version.getComment());
        item.put("createdAt", version.getCreatedAt());
        User creator = version.getCreatedBy();
        Map<String, Object> createdBy = new LinkedHashMap<>();
        createdBy.put("id", creator.getId());
        createdBy.put("username", creator.getUsername());
        createdBy.put("displayName", creator.getDisplayName());
        item.put("createdBy", createdBy);
        return item;
    }

    /**
     * 分页获取版本历史
     */
    @Transactional(readOnly = true)
    public Page<FileVersion> getVersionHistory(UUID fileId, Pageable pageable) {
        return fileVersionRepository.findByFileId(fileId, pageable);
    }

    /**
     * 获取特定版本详情
     */
    @Transactional(readOnly = true)
    public FileVersion getVersion(UUID fileId, int versionNumber) {
        List<FileVersion> versions = fileVersionRepository.findByFileIdOrderByVersionNumberDesc(fileId);
        return versions.stream()
                .filter(v -> v.getVersionNumber() == versionNumber)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FileVersion", "file=" + fileId + ", version=" + versionNumber));
    }

    /**
     * 对比两个版本之间的文本差异
     *
     * @return Map 包含:
     *         - versionA: 版本号A
     *         - versionB: 版本号B
     *         - diff: 差异内容（unified diff 格式）
     *         - additions: 新增行数
     *         - deletions: 删除行数
     *         - changes: 修改行数
     */
    public Map<String, Object> diffVersions(UUID fileId, int versionA, int versionB) {
        FileVersion va = getVersion(fileId, versionA);
        FileVersion vb = getVersion(fileId, versionB);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileId", fileId.toString());
        result.put("versionA", versionA);
        result.put("versionB", versionB);
        result.put("versionACreatedAt", va.getCreatedAt());
        result.put("versionBCreatedAt", vb.getCreatedAt());
        result.put("versionACreator", va.getCreatedBy().getDisplayName());
        result.put("versionBCreator", vb.getCreatedBy().getDisplayName());
        result.put("sizeDelta", vb.getFileSize() - va.getFileSize());

        // 尝试文本 diff
        try {
            String contentA = downloadVersionContent(va);
            String contentB = downloadVersionContent(vb);

            DiffResult diff = computeDiff(contentA, contentB);
            result.put("diff", diff.diffText);
            result.put("additions", diff.additions);
            result.put("deletions", diff.deletions);
            result.put("modifications", diff.modifications);
            result.put("diffFormat", "unified");
        } catch (Exception e) {
            log.warn("Cannot compute text diff for versions {}/{}: {}", versionA, versionB, e.getMessage());
            result.put("diff", "无法计算文本差异（可能为二进制文件）");
            result.put("diffFormat", "binary");
        }

        return result;
    }

    /**
     * 版本回滚 - 将文件恢复到指定历史版本
     *
     * @param fileId   文件ID
     * @param targetVersion 目标版本号
     * @param userId   操作者ID
     * @return 恢复后的文件
     */
    @Transactional
    public File rollbackToVersion(UUID fileId, int targetVersion, UUID userId) {
        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File", fileId.toString()));

        FileVersion targetVer = getVersion(fileId, targetVersion);

        // 读取目标版本内容
        byte[] content;
        try {
            content = downloadVersionBytes(targetVer);
        } catch (Exception e) {
            log.error("Failed to read version content for rollback: {}", targetVer.getStoragePath(), e);
            throw new RuntimeException("无法读取目标版本内容", e);
        }

        // 写入目标版本内容到当前文件
        try {
            uploadToStorage(file.getPath(), content, file.getMimeType());
        } catch (Exception e) {
            log.error("Failed to restore file content during rollback: {}", file.getPath(), e);
            throw new RuntimeException("版本回滚失败", e);
        }

        // 更新文件元数据
        file.setSize((long) content.length);
        file.setHash(targetVer.getFileHash());
        // 版本号由 createVersion 自动递增

        // 创建回滚记录
        createVersion(file, content,
                "回滚至版本 " + targetVersion + "（由用户 " + userId + " 操作）", userId);

        log.info("File {} rolled back from version {} to version {}", fileId, file.getVersion(), targetVersion);
        return file;
    }

    /**
     * 获取编辑痕迹追踪 - 谁在何时做了什么修改
     *
     * @return 修改记录列表
     */
    public List<Map<String, Object>> getEditHistory(UUID fileId) {
        List<FileVersion> versions = fileVersionRepository.findByFileIdOrderByVersionNumberDesc(fileId);

        return versions.stream().map(v -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("versionNumber", v.getVersionNumber());
            entry.put("createdBy", v.getCreatedBy().getDisplayName());
            entry.put("userId", v.getCreatedBy().getId().toString());
            entry.put("createdAt", v.getCreatedAt());
            entry.put("fileSize", v.getFileSize());
            entry.put("fileHash", v.getFileHash());
            entry.put("comment", v.getComment());
            return entry;
        }).collect(Collectors.toList());
    }

    /**
     * 删除文件的所有版本
     */
    @Transactional
    public void deleteVersionsByFileId(UUID fileId) {
        List<FileVersion> versions = fileVersionRepository.findByFileIdOrderByVersionNumberDesc(fileId);
        for (FileVersion v : versions) {
            try {
                deleteFromStorage(v.getStoragePath());
            } catch (Exception e) {
                log.warn("Failed to delete version file: {}", v.getStoragePath(), e);
            }
        }
        fileVersionRepository.deleteByFileId(fileId);
        log.info("All versions deleted for file: {}", fileId);
    }

    // ==================== 内部方法 ====================

    private String calculateHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content);
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String buildVersionPath(File file, int version) {
        return String.format("versions/%s/v%d_%s",
                file.getId(), version, file.getName());
    }

    private DiffResult computeDiff(String textA, String textB) {
        List<String> linesA = textA.lines().toList();
        List<String> linesB = textB.lines().toList();

        // 简化的 LCS diff 算法
        int[][] dp = new int[linesA.size() + 1][linesB.size() + 1];
        for (int i = 1; i <= linesA.size(); i++) {
            for (int j = 1; j <= linesB.size(); j++) {
                if (linesA.get(i - 1).equals(linesB.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        // 回溯生成 unified diff
        StringBuilder diffOut = new StringBuilder();
        int additions = 0, deletions = 0;
        int i = linesA.size(), j = linesB.size();
        List<String> diffLines = new ArrayList<>();

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && linesA.get(i - 1).equals(linesB.get(j - 1))) {
                diffLines.add(0, "  " + linesA.get(i - 1));
                i--; j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                diffLines.add(0, "+ " + linesB.get(j - 1));
                additions++;
                j--;
            } else if (i > 0) {
                diffLines.add(0, "- " + linesA.get(i - 1));
                deletions++;
                i--;
            }
        }

        diffOut.append("--- 版本A\n");
        diffOut.append("+++ 版本B\n");
        diffOut.append(String.format("@@ -%d,%d +%d,%d @@\n",
                Math.max(1, linesA.size()), linesA.size(),
                Math.max(1, linesB.size()), linesB.size()));

        // 限制输出行数
        int maxLines = Math.min(diffLines.size(), 500);
        for (int k = 0; k < maxLines; k++) {
            diffOut.append(diffLines.get(k)).append("\n");
        }
        if (diffLines.size() > maxLines) {
            diffOut.append("... (" + (diffLines.size() - maxLines) + " more lines) ...\n");
        }

        int modifications = Math.min(deletions, additions);
        return new DiffResult(diffOut.toString(), additions, deletions, modifications);
    }

    private File applyNewVersion(File file, byte[] newContent, String newName, String newMimeType,
                                  String newFileType, String comment, UUID userId, boolean clientEncrypted) {
        try {
            byte[] currentContent = downloadFileBytes(file.getPath());
            createVersion(file, currentContent, "保存新版本前的快照 - 版本 " + file.getVersion(), userId);
        } catch (Exception e) {
            log.error("Failed to snapshot current version before update: {}", file.getPath(), e);
            throw new RuntimeException("保存新版本前快照失败", e);
        }

        byte[] contentToStore = newContent;
        String storageMimeType = newMimeType;
        if (Boolean.TRUE.equals(file.getIsEncrypted())
                && !File.EncryptionMode.CLIENT.equals(file.getEncryptionMode())) {
            try {
                SecretKey fileKey = keyManagementService.unwrapFileKey(file.getEncryptionKeyId(), userId);
                contentToStore = encryptionService.encryptAesGcm(newContent, fileKey);
                storageMimeType = "application/octet-stream";
            } catch (Exception e) {
                throw new RuntimeException("新版本文件加密失败", e);
            }
        }
        if (clientEncrypted) {
            contentToStore = newContent;
            storageMimeType = "application/octet-stream";
        }

        try {
            uploadToStorage(file.getPath(), contentToStore, storageMimeType);
        } catch (Exception e) {
            throw new RuntimeException("新版本文件保存失败", e);
        }

        file.setName(newName);
        file.setSize((long) newContent.length);
        file.setHash(calculateHash(newContent));
        file.setMimeType(newMimeType);
        file.setFileType(newFileType);
        file.setSummary(null);
        file.setAiStatus(FileTypeDetector.isArchiveFileType(newFileType) ? File.AiStatus.SKIPPED : File.AiStatus.PENDING);
        file.setSensitiveLevel(File.SensitiveLevel.NORMAL);
        if (clientEncrypted) {
            file.setEncryptionMode(File.EncryptionMode.CLIENT);
            file.setIsEncrypted(true);
            file.setEncryptionKeyId(null);
            file.setAiStatus(File.AiStatus.COMPLETED);
            file.setAiErrorMessage("end-to-end encrypted file, server cannot analyze");
        }
        fileRepository.save(file);

        createVersion(file, contentToStore, comment, userId);
        return file;
    }

    private boolean isTextEditable(File file) {
        String lowerName = file.getName() == null ? "" : file.getName().toLowerCase(Locale.ROOT);
        int dot = lowerName.lastIndexOf('.');
        if (dot >= 0 && dot < lowerName.length() - 1) {
            String extension = lowerName.substring(dot + 1);
            if (TEXT_EDITABLE_EXTENSIONS.contains(extension)) {
                return true;
            }
        }
        return file.getFileType() != null
                && TEXT_EDITABLE_EXTENSIONS.contains(file.getFileType().toLowerCase(Locale.ROOT));
    }

    // ---- 存储代理 ----

    private String downloadVersionContent(FileVersion version) throws Exception {
        byte[] bytes = downloadVersionBytes(version);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] downloadVersionBytes(FileVersion version) throws Exception {
        try (InputStream is = downloadFromStorage(version.getStoragePath())) {
            return is.readAllBytes();
        }
    }

    private byte[] downloadFileBytes(String objectName) throws Exception {
        try (InputStream is = downloadFromStorage(objectName)) {
            return is.readAllBytes();
        }
    }

    private void uploadToStorage(String path, byte[] content, String mimeType) throws Exception {
        storageService.uploadFile(path, content, mimeType);
    }

    private InputStream downloadFromStorage(String path) throws Exception {
        return storageService.downloadFile(path);
    }

    private void deleteFromStorage(String path) throws Exception {
        storageService.deleteFile(path);
    }

    // ---- 内部类 ----
    private record DiffResult(String diffText, int additions, int deletions, int modifications) {}
}
