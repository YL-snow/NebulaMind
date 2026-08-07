package com.nebulamind.cloud.s3;

import com.nebulamind.entity.File;
import com.nebulamind.repository.UserRepository;
import com.nebulamind.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/s3")
@RequiredArgsConstructor
public class S3Controller {

    private final FileService fileService;
    private final UserRepository userRepository;

    @GetMapping("/{bucket}/objects")
    public ResponseEntity<String> listObjects(Authentication authentication,
                                              @PathVariable String bucket,
                                              @RequestParam(required = false) String prefix,
                                              @RequestParam(defaultValue = "1000") Integer maxKeys) {
        UUID userId = getUserIdFromAuthentication(authentication);
        
        String response = buildListObjectsResponse(userId, prefix, maxKeys);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/xml; charset=utf-8")
                .body(response);
    }

    @GetMapping("/{bucket}/objects/{fileId}")
    public ResponseEntity<InputStream> getObject(Authentication authentication,
                                                  @PathVariable String bucket,
                                                  @PathVariable String fileId) throws Exception {
        UUID userId = getUserIdFromAuthentication(authentication);
        
        InputStream inputStream = fileService.downloadFile(UUID.fromString(fileId), userId);
        File file = fileService.getFileById(UUID.fromString(fileId), userId);

        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(file.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.getSize()))
                .body(inputStream);
    }

    @RequestMapping(value = "/{bucket}/objects/{fileId}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headObject(Authentication authentication,
                                            @PathVariable String bucket,
                                            @PathVariable String fileId) {
        UUID userId = getUserIdFromAuthentication(authentication);
        
        try {
            File file = fileService.getFileById(UUID.fromString(fileId), userId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, file.getMimeType())
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.getSize()))
                    .header(HttpHeaders.ETAG, "\"" + file.getHash() + "\"")
                    .build();
        } catch (Exception e) {
            log.error("Failed to head object via S3 API", e);
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{bucket}/objects/{fileId}")
    public ResponseEntity<String> putObject(Authentication authentication,
                                             @PathVariable String bucket,
                                             @PathVariable String fileId,
                                             @RequestBody byte[] content,
                                             @RequestHeader(value = "Content-Type", defaultValue = "application/octet-stream") String contentType) {
        UUID userId = getUserIdFromAuthentication(authentication);
        
        try {
            File existingFile;
            try {
                existingFile = fileService.getFileById(UUID.fromString(fileId), userId);
            } catch (Exception e) {
                existingFile = null;
            }

            String fileName = fileId;
            if (existingFile != null) {
                fileName = existingFile.getName();
            }

            com.nebulamind.dto.FileRequest request = com.nebulamind.dto.FileRequest.builder()
                    .name(fileName)
                    .mimeType(contentType)
                    .size((long) content.length)
                    .content(content)
                    .build();

            if (existingFile != null) {
                fileService.updateFile(UUID.fromString(fileId), request, userId);
            } else {
                fileService.createFile(request, userId);
            }

            String response = buildPutObjectResponse(fileId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/xml; charset=utf-8")
                    .body(response);
        } catch (Exception e) {
            log.error("Failed to put object via S3 API", e);
            return ResponseEntity.status(500).build();
        }
    }

    @DeleteMapping("/{bucket}/objects/{fileId}")
    public ResponseEntity<String> deleteObject(Authentication authentication,
                                                @PathVariable String bucket,
                                                @PathVariable String fileId) {
        UUID userId = getUserIdFromAuthentication(authentication);
        
        try {
            fileService.deleteFile(UUID.fromString(fileId), userId);
            
            String response = buildDeleteObjectResponse(fileId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/xml; charset=utf-8")
                    .body(response);
        } catch (Exception e) {
            log.error("Failed to delete object via S3 API", e);
            return ResponseEntity.status(500).build();
        }
    }

    private String buildListObjectsResponse(UUID userId, String prefix, Integer maxKeys) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">\n");
        xml.append("  <Name>nebulamind</Name>\n");
        xml.append("  <IsTruncated>false</IsTruncated>\n");
        
        java.util.List<File> files = fileService.getUserFiles(userId, org.springframework.data.domain.PageRequest.of(0, maxKeys)).getContent();
        
        for (File file : files) {
            if (prefix != null && !file.getName().startsWith(prefix)) {
                continue;
            }
            
            xml.append("  <Contents>\n");
            xml.append("    <Key>").append(file.getId()).append("</Key>\n");
            xml.append("    <LastModified>").append(formatDate(file.getUpdatedAt())).append("</LastModified>\n");
            xml.append("    <ETag>\"").append(file.getHash()).append("\"</ETag>\n");
            xml.append("    <Size>").append(file.getSize()).append("</Size>\n");
            xml.append("    <StorageClass>STANDARD</StorageClass>\n");
            xml.append("  </Contents>\n");
        }
        
        xml.append("</ListBucketResult>");
        return xml.toString();
    }

    private String buildPutObjectResponse(String fileId) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<PutObjectResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">\n");
        xml.append("  <ETag>\"").append(fileId).append("\"</ETag>\n");
        xml.append("</PutObjectResult>");
        return xml.toString();
    }

    private String buildDeleteObjectResponse(String fileId) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<DeleteResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">\n");
        xml.append("  <Deleted>\n");
        xml.append("    <Key>").append(fileId).append("</Key>\n");
        xml.append("  </Deleted>\n");
        xml.append("</DeleteResult>");
        return xml.toString();
    }

    private String formatDate(java.time.LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private UUID getUserIdFromAuthentication(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(user -> user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
