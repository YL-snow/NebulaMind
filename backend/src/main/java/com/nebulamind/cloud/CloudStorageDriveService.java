package com.nebulamind.cloud;

import com.nebulamind.dto.CloudStorageItem;
import com.nebulamind.dto.FileRequest;
import com.nebulamind.entity.CloudStorageConfig;
import com.nebulamind.entity.File;
import com.nebulamind.repository.FileRepository;
import com.nebulamind.service.FileService;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CloudStorageDriveService {

    private static final String PROPFIND_BODY =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<D:propfind xmlns:D=\"DAV:\"><D:allprop/></D:propfind>";

    private static final Map<String, String> EXTENSION_MIME_TYPES = Map.ofEntries(
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("zip", "application/zip"),
            Map.entry("rar", "application/vnd.rar"),
            Map.entry("7z", "application/x-7z-compressed"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"));

    private final FileRepository fileRepository;
    private final FileService fileService;
    private final OkHttpClient webDavHttpClient;

    public CloudStorageDriveService(FileRepository fileRepository,
                                    FileService fileService) {
        this.fileRepository = fileRepository;
        this.fileService = fileService;
        this.webDavHttpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public List<CloudStorageItem> listFiles(CloudStorageConfig config, String path) throws Exception {
        return switch (config.getProviderType().toUpperCase(Locale.ROOT)) {
            case "S3" -> listS3(config, path);
            case "WEBDAV" -> listWebDav(config, path);
            default -> throw new IllegalArgumentException("该存储类型暂不支持文件浏览，请使用 S3 或 WebDAV");
        };
    }

    public byte[] downloadFile(CloudStorageConfig config, String path) throws Exception {
        String normalizedPath = requirePath(path);
        byte[] content = switch (config.getProviderType().toUpperCase(Locale.ROOT)) {
            case "S3" -> downloadS3(config, normalizedPath);
            case "WEBDAV" -> downloadWebDav(config, normalizedPath);
            default -> throw new IllegalArgumentException("该存储类型暂不支持下载");
        };
        return content;
    }

    public void uploadFile(CloudStorageConfig config, String folderPath, String fileName,
                           byte[] content, String contentType) throws Exception {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String rawFileName = fileName;
        fileName = fixSubmittedFilename(fileName);
        log.info("Cloud drive upload requested: provider={}, folder={}, rawName={}, fixedName={}",
                config.getProviderType(), folderPath, rawFileName, fileName);
        String resolvedContentType = (contentType == null || contentType.isBlank()
                || "application/octet-stream".equalsIgnoreCase(contentType.trim()))
                ? guessMimeType(fileName) : contentType;
        switch (config.getProviderType().toUpperCase(Locale.ROOT)) {
            case "S3" -> uploadS3(config, folderPath, fileName, content, resolvedContentType);
            case "WEBDAV" -> uploadWebDav(config, folderPath, fileName, content, resolvedContentType);
            default -> throw new IllegalArgumentException("该存储类型暂不支持上传");
        }
    }

    public void deleteFile(CloudStorageConfig config, String path) throws Exception {
        String normalizedPath = requirePath(path);
        switch (config.getProviderType().toUpperCase(Locale.ROOT)) {
            case "S3" -> deleteS3(config, normalizedPath);
            case "WEBDAV" -> deleteWebDav(config, normalizedPath);
            default -> throw new IllegalArgumentException("该存储类型暂不支持删除");
        }
    }

    public Map<String, Object> importFile(CloudStorageConfig config, UUID userId, String path, String displayName) throws Exception {
        String normalizedPath = requirePath(path);
        String remoteKey = buildRemoteKey(config, normalizedPath);

        Optional<File> existing = fileRepository.findByCloudDriveFileId(remoteKey);
        if (existing.isPresent()) {
            return Map.of(
                    "imported", false,
                    "duplicate", true,
                    "message", "该云文件已导入过",
                    "fileId", existing.get().getId().toString());
        }

        byte[] content = downloadFile(config, normalizedPath);
        String resolvedDisplayName = (displayName != null && !displayName.isBlank())
                ? displayName.trim() : lastSegment(normalizedPath);
        String name = fixSubmittedFilename(resolvedDisplayName);
        log.info("Cloud file import requested: config={}, path={}, displayName={}, resolvedName={}",
                config.getId(), normalizedPath, displayName, name);
        String mimeType = guessMimeType(name);
        FileRequest request = FileRequest.builder()
                .name(name)
                .mimeType(mimeType)
                .size((long) content.length)
                .content(content)
                .build();

        File created;
        try {
            created = fileService.createFile(request, userId, false);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("File already exists")) {
                return Map.of(
                        "imported", false,
                        "duplicate", true,
                        "message", "文件内容已存在于 NebulaMind，无需重复导入");
            }
            throw e;
        }
        created.setCloudDriveFileId(remoteKey);
        fileService.saveFile(created);
        log.info("Imported cloud file into NebulaMind: config={}, path={}, fileId={}",
                config.getId(), normalizedPath, created.getId());

        return Map.of(
                "imported", true,
                "duplicate", false,
                "message", "导入成功",
                "fileId", created.getId().toString());
    }

    public TestResult testConnection(CloudStorageConfig config) {
        switch (config.getProviderType().toUpperCase(Locale.ROOT)) {
            case "S3":
                return testS3(config);
            case "WEBDAV":
                return testWebDav(config);
            default:
                return new TestResult(false, "不支持的存储类型: " + config.getProviderType());
        }
    }

    // ---------- S3 ----------

    private List<CloudStorageItem> listS3(CloudStorageConfig config, String path) throws Exception {
        String bucket = requireBucket(config);
        MinioClient client = buildS3Client(config);
        String prefix = normalizeS3FolderPrefix(path);

        List<CloudStorageItem> items = new ArrayList<>();
        Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(prefix)
                .delimiter("/")
                .recursive(false)
                .build());

        for (Result<Item> result : results) {
            Item item = result.get();
            String objectName = item.objectName();
            if (item.isDir() || objectName.endsWith("/")) {
                String folderName = stripTrailingSlash(objectName);
                items.add(CloudStorageItem.builder()
                        .path(folderName + "/")
                        .name(fixSubmittedFilename(lastSegment(folderName)))
                        .folder(true)
                        .build());
            } else {
                items.add(CloudStorageItem.builder()
                        .path(objectName)
                        .name(fixSubmittedFilename(lastSegment(objectName)))
                        .folder(false)
                        .size(item.size())
                        .mimeType(guessMimeType(objectName))
                        .updatedAt(item.lastModified() == null ? null
                                : LocalDateTime.ofInstant(item.lastModified().toInstant(), ZoneId.systemDefault()))
                        .build());
            }
        }
        return items;
    }

    private byte[] downloadS3(CloudStorageConfig config, String path) throws Exception {
        String bucket = requireBucket(config);
        MinioClient client = buildS3Client(config);
        String objectName = stripLeadingSlash(path);
        try (InputStream in = client.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build())) {
            return in.readAllBytes();
        }
    }

    private void uploadS3(CloudStorageConfig config, String folderPath, String fileName,
                          byte[] content, String contentType) throws Exception {
        String bucket = requireBucket(config);
        MinioClient client = buildS3Client(config);
        String prefix = normalizeS3FolderPrefix(folderPath);
        String objectName = prefix + fileName;
        client.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .contentType(contentType == null || contentType.isBlank()
                        ? guessMimeType(fileName) : contentType)
                .build());
        log.info("Uploaded file to S3 storage: bucket={}, object={}", bucket, objectName);
    }

    private void deleteS3(CloudStorageConfig config, String path) throws Exception {
        String bucket = requireBucket(config);
        MinioClient client = buildS3Client(config);
        String objectName = stripLeadingSlash(path);
        client.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
        log.info("Deleted file from S3 storage: bucket={}, object={}", bucket, objectName);
    }

    private TestResult testS3(CloudStorageConfig config) {
        String endpoint = config.getEndpointUrl();
        if (endpoint == null || endpoint.isBlank()) {
            return new TestResult(false, "Endpoint URL 不能为空");
        }
        try {
            MinioClient client = buildS3Client(config);
            client.listBuckets();
            return new TestResult(true, "S3 连接成功，已列出存储桶");
        } catch (Exception e) {
            return new TestResult(false, "S3 连接失败: " + e.getMessage());
        }
    }

    private MinioClient buildS3Client(CloudStorageConfig config) {
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(config.getEndpointUrl())
                .credentials(config.getAccessKey(), config.getSecretKey());
        String region = config.getRegion();
        if (region != null && region.matches("[A-Za-z0-9-]+")) {
            builder.region(region.trim());
        }
        return builder.build();
    }

    // ---------- WebDAV ----------

    private List<CloudStorageItem> listWebDav(CloudStorageConfig config, String path) throws Exception {
        String baseUrl = requireEndpoint(config);
        String davPath = normalizeWebDavPath(path);
        Request request = new Request.Builder()
                .url(joinWebDavUrl(baseUrl, davPath))
                .header("Authorization", basicAuth(config))
                .header("Depth", "1")
                .method("PROPFIND", RequestBody.create(
                        PROPFIND_BODY, MediaType.parse("application/xml; charset=utf-8")))
                .build();

        try (Response response = webDavHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("WebDAV 列表请求失败: HTTP " + response.code());
            }
            String body = response.body() == null ? "" : response.body().string();
            return parsePropfind(body, baseUrl, davPath);
        }
    }

    private byte[] downloadWebDav(CloudStorageConfig config, String path) throws Exception {
        String baseUrl = requireEndpoint(config);
        Request request = new Request.Builder()
                .url(joinWebDavUrl(baseUrl, path))
                .header("Authorization", basicAuth(config))
                .get()
                .build();
        try (Response response = webDavHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("WebDAV 下载失败: HTTP " + response.code());
            }
            return response.body() == null ? new byte[0] : response.body().bytes();
        }
    }

    private void uploadWebDav(CloudStorageConfig config, String folderPath, String fileName,
                              byte[] content, String contentType) throws Exception {
        String baseUrl = requireEndpoint(config);
        String folder = normalizeWebDavPath(folderPath);
        String targetPath = folder + "/" + encodeSegment(fileName);
        Request request = new Request.Builder()
                .url(joinWebDavUrl(baseUrl, targetPath))
                .header("Authorization", basicAuth(config))
                .header("Content-Type", contentType == null || contentType.isBlank()
                        ? guessMimeType(fileName) : contentType)
                .put(RequestBody.create(content, null))
                .build();
        try (Response response = webDavHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("WebDAV 上传失败: HTTP " + response.code());
            }
        }
    }

    private void deleteWebDav(CloudStorageConfig config, String path) throws Exception {
        String baseUrl = requireEndpoint(config);
        Request request = new Request.Builder()
                .url(joinWebDavUrl(baseUrl, path))
                .header("Authorization", basicAuth(config))
                .delete()
                .build();
        try (Response response = webDavHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("WebDAV 删除失败: HTTP " + response.code());
            }
        }
    }

    private TestResult testWebDav(CloudStorageConfig config) {
        String baseUrl = config.getEndpointUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return new TestResult(false, "WebDAV 地址不能为空");
        }
        try {
            List<CloudStorageItem> items = listWebDav(config, "");
            return new TestResult(true, "WebDAV 连接成功，共 " + items.size() + " 个顶层项目");
        } catch (Exception e) {
            return new TestResult(false, "WebDAV 连接失败: " + e.getMessage());
        }
    }

    private List<CloudStorageItem> parsePropfind(String xml, String baseUrl, String requestedPath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        Document doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        String basePath = pathOf(URI.create(baseUrl).getPath());
        String selfPath = requestedPath == null || requestedPath.isBlank() ? "/" : requestedPath;

        List<CloudStorageItem> items = new ArrayList<>();
        NodeList responses = doc.getElementsByTagNameNS("*", "response");
        for (int i = 0; i < responses.getLength(); i++) {
            Node node = responses.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element responseEl = (Element) node;
            String href = textOfChild(responseEl, "href");
            if (href == null || href.isBlank()) {
                continue;
            }
            String itemPath = extractWebDavPath(href);
            if (itemPath == null || itemPath.isBlank()) {
                continue;
            }
            String relativePath = relativizeWebDavPath(itemPath, basePath);
            if (relativePath == null
                    || relativePath.equals(selfPath)
                    || relativePath.equals(selfPath + "/")) {
                continue;
            }
            String normalizedItemPath = relativePath;

            String displayName = propValue(responseEl, "displayname");
            String name = fixSubmittedFilename(displayName != null && !displayName.isBlank()
                    ? displayName : lastSegment(normalizedItemPath));
            boolean folder = isCollection(responseEl);
            Long size = parseLongSafe(propValue(responseEl, "getcontentlength"));
            String mimeType = propValue(responseEl, "getcontenttype");
            LocalDateTime updatedAt = parseDateSafe(propValue(responseEl, "getlastmodified"));

            items.add(CloudStorageItem.builder()
                    .path(folder && !normalizedItemPath.endsWith("/")
                            ? normalizedItemPath + "/" : normalizedItemPath)
                    .name(name)
                    .folder(folder)
                    .size(size)
                    .mimeType(mimeType == null || mimeType.isBlank() ? null : mimeType)
                    .updatedAt(updatedAt)
                    .build());
        }
        return items;
    }

    private boolean isCollection(Element responseEl) {
        NodeList children = responseEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE || !"propstat".equals(child.getLocalName())) {
                continue;
            }
            Element propstat = (Element) child;
            Element prop = firstElementChild(propstat, "prop");
            if (prop == null) {
                continue;
            }
            for (int j = 0; j < prop.getChildNodes().getLength(); j++) {
                Node propChild = prop.getChildNodes().item(j);
                if (propChild.getNodeType() == Node.ELEMENT_NODE
                        && "resourcetype".equals(propChild.getLocalName())) {
                    Element resourcetype = (Element) propChild;
                    if (firstElementChild(resourcetype, "collection") != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Element firstElementChild(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private String propValue(Element responseEl, String localName) {
        NodeList children = responseEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE || !"propstat".equals(child.getLocalName())) {
                continue;
            }
            Element prop = firstElementChild((Element) child, "prop");
            if (prop == null) {
                continue;
            }
            String value = textOfChild(prop, localName);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String textOfChild(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                return child.getTextContent() == null ? "" : child.getTextContent().trim();
            }
        }
        return "";
    }

    private String extractWebDavPath(String href) {
        String value = href.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            try {
                value = URI.create(value).getPath();
            } catch (Exception e) {
                return null;
            }
        }
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        if (value.startsWith("/")) {
            return value;
        }
        return "/" + value;
    }

    private String pathOf(String value) {
        if (value == null || value.isBlank() || "/".equals(value)) {
            return "/";
        }
        return value;
    }

    private String relativizeWebDavPath(String itemPath, String basePath) {
        String normalizedItem = pathOf(itemPath);
        String normalizedBase = pathOf(basePath);
        while (normalizedBase.length() > 1 && normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        if ("/".equals(normalizedBase)) {
            return normalizedItem;
        }
        if (normalizedItem.equals(normalizedBase) || normalizedItem.equals(normalizedBase + "/")) {
            return "/";
        }
        if (normalizedItem.startsWith(normalizedBase + "/")) {
            return normalizedItem.substring(normalizedBase.length());
        }
        return null;
    }

    // ---------- 工具方法 ----------

    private String requirePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("云文件路径不能为空");
        }
        return path.trim();
    }

    private String requireBucket(CloudStorageConfig config) {
        if (config.getBucketName() == null || config.getBucketName().isBlank()) {
            throw new IllegalArgumentException("S3 存储桶名称不能为空");
        }
        return config.getBucketName().trim();
    }

    private String requireEndpoint(CloudStorageConfig config) {
        if (config.getEndpointUrl() == null || config.getEndpointUrl().isBlank()) {
            throw new IllegalArgumentException("存储服务地址不能为空");
        }
        return config.getEndpointUrl().trim();
    }

    private String normalizeS3FolderPrefix(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "";
        }
        String prefix = stripLeadingSlash(path.trim());
        if (!prefix.endsWith("/")) {
            prefix += "/";
        }
        return prefix;
    }

    private String stripLeadingSlash(String value) {
        String result = value;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String normalizeWebDavPath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "";
        }
        String value = path.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        return value.endsWith("/") && value.length() > 1
                ? value.substring(0, value.length() - 1) : value;
    }

    private String joinWebDavUrl(String baseUrl, String path) {
        String base = baseUrl.replaceAll("/+$", "");
        if (path == null || path.isBlank() || "/".equals(path)) {
            return base + "/";
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        return base + normalized;
    }

    private String encodeSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String basicAuth(CloudStorageConfig config) {
        String credentials = (config.getAccessKey() == null ? "" : config.getAccessKey())
                + ":" + (config.getSecretKey() == null ? "" : config.getSecretKey());
        return "Basic " + java.util.Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String lastSegment(String path) {
        String value = stripTrailingSlash(path);
        int slash = value.lastIndexOf('/');
        if (slash >= 0 && slash < value.length() - 1) {
            return value.substring(slash + 1);
        }
        return value;
    }

    private String fixSubmittedFilename(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return fileName;
        }
        boolean allLatin1 = true;
        for (int i = 0; i < fileName.length(); i++) {
            if (fileName.charAt(i) > 0xFF) {
                allLatin1 = false;
                break;
            }
        }
        if (!allLatin1) {
            return fileName;
        }
        String utf8 = new String(fileName.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        return utf8.contains("\uFFFD") ? fileName : utf8;
    }

    private String guessMimeType(String fileName) {
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0 && dot < fileName.length() - 1) {
                String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
                String mapped = EXTENSION_MIME_TYPES.get(ext);
                if (mapped != null) {
                    return mapped;
                }
            }
        }
        String guessed = URLConnection.guessContentTypeFromName(fileName);
        return guessed == null ? "application/octet-stream" : guessed;
    }

    private String buildRemoteKey(CloudStorageConfig config, String path) {
        return config.getId() + ":" + config.getProviderType() + ":" + sha256Short(path);
    }

    private String sha256Short(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    private Long parseLongSafe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime parseDateSafe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            return java.time.ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public record TestResult(boolean success, String message) {
    }
}
