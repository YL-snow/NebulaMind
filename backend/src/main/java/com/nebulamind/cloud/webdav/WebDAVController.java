package com.nebulamind.cloud.webdav;

import com.nebulamind.entity.File;
import com.nebulamind.repository.UserRepository;
import com.nebulamind.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/webdav")
@RequiredArgsConstructor
public class WebDAVController {

    private final FileService fileService;
    private final UserRepository userRepository;

    @RequestMapping(value = {"", "/**"}, method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<?> handleGetRequest(HttpServletRequest request, Authentication authentication) {
        String webdavMethod = (String) request.getAttribute("webdav.method");
        if (webdavMethod != null) {
            return handleWebDAVMethod(webdavMethod, request, authentication);
        }

        String method = request.getMethod();
        String requestURI = request.getRequestURI();
        String fileId = extractFileId(requestURI);

        if (fileId == null || fileId.isEmpty()) {
            return propfind(authentication, request.getHeader("Depth"), null);
        }

        try {
            UUID userId = getUserIdFromAuthentication(authentication);
            UUID uuid = UUID.fromString(fileId);

            if ("HEAD".equalsIgnoreCase(method)) {
                File file = fileService.getFileById(uuid, userId);
                return ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.parseMediaType(file.getMimeType()))
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.getSize()))
                        .build();
            }

            InputStream inputStream = fileService.downloadFile(uuid, userId);
            File file = fileService.getFileById(uuid, userId);

            String encodedFileName = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType(file.getMimeType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"; filename*=UTF-8''" + encodedFileName)
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.getSize()))
                    .body(inputStream.readAllBytes());
        } catch (Exception e) {
            log.error("Failed to handle WebDAV GET/HEAD request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ResponseEntity<?> handleWebDAVMethod(String method, HttpServletRequest request, Authentication authentication) {
        String requestURI = request.getRequestURI();
        String fileId = extractFileId(requestURI);

        if ("PROPFIND".equalsIgnoreCase(method)) {
            return propfind(authentication, request.getHeader("Depth"), fileId);
        } else if ("MKCOL".equalsIgnoreCase(method)) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } else if ("COPY".equalsIgnoreCase(method)) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        } else if ("MOVE".equalsIgnoreCase(method)) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PutMapping("/**")
    public ResponseEntity<Void> putFile(HttpServletRequest request, Authentication authentication, @RequestBody byte[] content) {
        String requestURI = request.getRequestURI();
        String fileId = extractFileId(requestURI);

        if (fileId == null || fileId.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        UUID userId = getUserIdFromAuthentication(authentication);

        try {
            File existingFile;
            try {
                existingFile = fileService.getFileById(UUID.fromString(fileId), userId);
            } catch (Exception e) {
                existingFile = null;
            }

            if (existingFile == null) {
                existingFile = fileService.findFileByContent(userId, content).orElse(null);
            }
            if (existingFile != null) {
                return ResponseEntity.ok().build();
            }

            String contentType = request.getHeader("Content-Type");
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            String fileName = fileId;
            if (existingFile != null) {
                fileName = existingFile.getName();
            }

            com.nebulamind.dto.FileRequest req = com.nebulamind.dto.FileRequest.builder()
                    .name(fileName)
                    .mimeType(contentType)
                    .size((long) content.length)
                    .content(content)
                    .build();

            if (existingFile != null) {
                fileService.updateFile(UUID.fromString(fileId), req, userId);
            } else {
                fileService.createFile(req, userId);
            }

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to upload file via WebDAV", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/**")
    public ResponseEntity<Void> deleteFile(HttpServletRequest request, Authentication authentication) {
        String requestURI = request.getRequestURI();
        String fileId = extractFileId(requestURI);

        if (fileId == null || fileId.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        UUID userId = getUserIdFromAuthentication(authentication);

        try {
            fileService.deleteFile(UUID.fromString(fileId), userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to delete file via WebDAV", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ResponseEntity<String> propfind(Authentication authentication, String depth, String fileId) {
        UUID userId = getUserIdFromAuthentication(authentication);

        String response = buildPropfindResponse(userId, fileId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/xml; charset=utf-8")
                .body(response);
    }

    private String extractFileId(String requestURI) {
        if (requestURI.startsWith("/webdav/")) {
            String path = requestURI.substring("/webdav/".length());
            int idx = path.indexOf('/');
            String segment = idx > 0 ? path.substring(0, idx) : path;
            return URLDecoder.decode(segment, StandardCharsets.UTF_8);
        }
        return null;
    }

    private String buildPropfindResponse(UUID userId, String fileId) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<D:multistatus xmlns:D=\"DAV:\">\n");

        if (fileId == null || fileId.isEmpty()) {
            xml.append("  <D:response>\n");
            xml.append("    <D:href>/webdav/</D:href>\n");
            xml.append("    <D:propstat>\n");
            xml.append("      <D:prop>\n");
            xml.append("        <D:displayname>NebulaMind</D:displayname>\n");
            xml.append("        <D:resourcetype><D:collection/></D:resourcetype>\n");
            xml.append("      </D:prop>\n");
            xml.append("      <D:status>HTTP/1.1 200 OK</D:status>\n");
            xml.append("    </D:propstat>\n");
            xml.append("  </D:response>\n");
        }

        List<File> files;
        if (fileId != null && !fileId.isEmpty()) {
            try {
                File file = fileService.getFileById(UUID.fromString(fileId), userId);
                files = List.of(file);
            } catch (Exception e) {
                files = List.of();
            }
        } else {
            files = fileService.getUserFiles(userId, org.springframework.data.domain.PageRequest.of(0, 100)).getContent();
        }

        for (File file : files) {
            xml.append("  <D:response>\n");
            xml.append("    <D:href>/webdav/").append(file.getId()).append("</D:href>\n");
            xml.append("    <D:propstat>\n");
            xml.append("      <D:prop>\n");
            xml.append("        <D:displayname>").append(escapeXml(file.getName())).append("</D:displayname>\n");
            xml.append("        <D:getcontentlength>").append(file.getSize()).append("</D:getcontentlength>\n");
            xml.append("        <D:getcontenttype>").append(file.getMimeType()).append("</D:getcontenttype>\n");
            xml.append("        <D:getlastmodified>").append(formatDate(file.getUpdatedAt())).append("</D:getlastmodified>\n");
            xml.append("      </D:prop>\n");
            xml.append("      <D:status>HTTP/1.1 200 OK</D:status>\n");
            xml.append("    </D:propstat>\n");
            xml.append("  </D:response>\n");
        }

        xml.append("</D:multistatus>");
        return xml.toString();
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String formatDate(java.time.LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                .format(dateTime.atZone(java.time.ZoneId.systemDefault()));
    }

    private UUID getUserIdFromAuthentication(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(user -> user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
