package com.nebulamind.controller;

import com.nebulamind.cloud.CloudDriveClient;
import com.nebulamind.cloud.CloudDriveFile;
import com.nebulamind.cloud.CloudDriveToken;
import com.nebulamind.cloud.CloudDriveSyncService;
import com.nebulamind.service.RedisCacheService;
import com.nebulamind.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/v1/cloud-drive")
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("!dev")
public class CloudDriveController {

    private final CloudDriveClient cloudDriveClient;
    private final CloudDriveSyncService cloudDriveSyncService;
    private final UserRepository userRepository;
    private final RedisCacheService redisCacheService;

    private static final String STATE_CACHE_PREFIX = "cloud_drive:state:";

    @GetMapping("/authorize")
    public ResponseEntity<Map<String, String>> getAuthorizeUrl(Authentication authentication) {
        UUID userId = getUserIdFromAuthentication(authentication);
        String state = UUID.randomUUID().toString();
        
        redisCacheService.set(STATE_CACHE_PREFIX + state, userId.toString(), 300, TimeUnit.SECONDS);
        
        String authorizeUrl = cloudDriveClient.buildAuthorizeUrl(state);

        Map<String, String> response = new HashMap<>();
        response.put("authorizeUrl", authorizeUrl);
        response.put("state", state);

        return ResponseEntity.ok(response);
    }

    @RequestMapping(value = "/callback", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, String>> callback(@RequestParam("code") String code,
                                                        @RequestParam("state") String state) throws IOException {
        String userIdStr = (String) redisCacheService.get(STATE_CACHE_PREFIX + state);
        if (userIdStr == null) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Invalid or expired state");
            return ResponseEntity.badRequest().body(response);
        }

        UUID userId = UUID.fromString(userIdStr);
        redisCacheService.delete(STATE_CACHE_PREFIX + state);

        CloudDriveToken token = cloudDriveClient.exchangeCodeForToken(code);
        cloudDriveClient.saveToken(userId, token);

        log.info("Cloud drive authenticated for user: {}", userId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Authentication successful");
        response.put("state", state);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/files")
    public ResponseEntity<List<CloudDriveFile>> listFiles(Authentication authentication,
                                                          @RequestParam(required = false) String parentId,
                                                          @RequestParam(defaultValue = "1") Integer page,
                                                          @RequestParam(defaultValue = "20") Integer size) throws IOException {
        UUID userId = getUserIdFromAuthentication(authentication);
        List<CloudDriveFile> files = cloudDriveClient.listFiles(userId, parentId, page, size);
        return ResponseEntity.ok(files);
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<CloudDriveFile> getFile(Authentication authentication, @PathVariable String fileId) throws IOException {
        UUID userId = getUserIdFromAuthentication(authentication);
        CloudDriveFile file = cloudDriveClient.getFile(userId, fileId);
        return ResponseEntity.ok(file);
    }

    @PostMapping("/files/upload")
    public ResponseEntity<CloudDriveFile> uploadFile(Authentication authentication,
                                                     @RequestParam("file") MultipartFile file,
                                                     @RequestParam(required = false) String parentId) throws IOException {
        UUID userId = getUserIdFromAuthentication(authentication);
        
        CloudDriveFile uploadedFile = cloudDriveClient.uploadFile(
                userId,
                file.getOriginalFilename(),
                parentId,
                file.getBytes(),
                file.getContentType()
        );
        
        return ResponseEntity.ok(uploadedFile);
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> deleteFile(Authentication authentication, @PathVariable String fileId) throws IOException {
        UUID userId = getUserIdFromAuthentication(authentication);
        cloudDriveClient.deleteFile(userId, fileId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/files/{fileId}/share")
    public ResponseEntity<CloudDriveFile> shareFile(Authentication authentication,
                                                    @PathVariable String fileId,
                                                    @RequestParam String shareType,
                                                    @RequestBody List<String> users) throws IOException {
        UUID userId = getUserIdFromAuthentication(authentication);
        CloudDriveFile sharedFile = cloudDriveClient.shareFile(userId, fileId, shareType, users);
        return ResponseEntity.ok(sharedFile);
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, String>> syncFiles(Authentication authentication) {
        UUID userId = getUserIdFromAuthentication(authentication);

        try {
            cloudDriveSyncService.syncFiles(userId);
            cloudDriveSyncService.syncDeletedFiles(userId);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Sync completed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to sync files", e);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Sync failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/sync/file/{fileId}")
    public ResponseEntity<Map<String, String>> syncFile(Authentication authentication, @PathVariable String fileId) {
        UUID userId = getUserIdFromAuthentication(authentication);

        try {
            CloudDriveFile cloudFile = cloudDriveClient.getFile(userId, fileId);
            cloudDriveSyncService.syncFile(userId, cloudFile);

            Map<String, String> response = new HashMap<>();
            response.put("message", "File sync completed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to sync file", e);
            Map<String, String> response = new HashMap<>();
            response.put("message", "File sync failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @DeleteMapping("/disconnect")
    public ResponseEntity<Map<String, String>> disconnect(Authentication authentication) {
        UUID userId = getUserIdFromAuthentication(authentication);
        cloudDriveClient.deleteToken(userId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Cloud drive disconnected successfully");
        return ResponseEntity.ok(response);
    }

    private UUID getUserIdFromAuthentication(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(user -> user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
