package com.nebulamind.controller;

import com.nebulamind.entity.User;
import com.nebulamind.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/e2ee")
@RequiredArgsConstructor
public class E2eeController {

    private final UserRepository userRepository;

    @GetMapping("/key")
    public ResponseEntity<Map<String, Object>> getKey(Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(Map.of(
                "blob", user.getE2eeKeyBlob() == null ? "" : user.getE2eeKeyBlob(),
                "userId", user.getId().toString()
        ));
    }

    @PutMapping("/key")
    public ResponseEntity<Map<String, Object>> saveKey(
            Authentication authentication,
            @RequestBody Map<String, String> request) {
        String blob = request.get("blob");
        if (blob == null || blob.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "blob is required"));
        }
        User user = currentUser(authentication);
        user.setE2eeKeyBlob(blob);
        userRepository.save(user);
        log.info("E2EE key blob saved for user: {}", user.getId());
        return ResponseEntity.ok(Map.of("success", true, "userId", user.getId().toString()));
    }

    private User currentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
