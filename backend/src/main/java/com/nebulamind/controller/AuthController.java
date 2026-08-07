package com.nebulamind.controller;

import com.nebulamind.dto.AuthRequest;
import com.nebulamind.dto.AuthResponse;
import com.nebulamind.dto.ChangePasswordRequest;
import com.nebulamind.dto.RegisterRequest;
import com.nebulamind.service.AuthService;
import com.nebulamind.service.SmsVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SmsVerificationService smsVerificationService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request for: {}", request.getEmail());
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        log.info("Login request for: {}", request.getEmail());
        return ResponseEntity.ok(authService.authenticate(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestParam String token) {
        log.info("Token refresh request");
        return ResponseEntity.ok(authService.refreshToken(token));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestParam String token) {
        log.info("Logout request");
        authService.logout(token);
        return ResponseEntity.ok().build();
    }

    // ---- 短信验证码相关端点 ----

    /**
     * 发送短信验证码
     */
    @PostMapping("/sms/send")
    public ResponseEntity<Map<String, Object>> sendSmsCode(@RequestBody Map<String, String> request) {
        String phone = request.get("phone");
        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "手机号不能为空"));
        }

        // 手机号格式校验
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "手机号格式不正确"));
        }

        boolean sent = smsVerificationService.sendCode(phone);
        if (sent) {
            return ResponseEntity.ok(Map.of("success", true, "message", "验证码已发送"));
        } else {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "验证码功能未启用或发送频率过高，请稍后重试",
                "dev_mode", true
            ));
        }
    }

    /**
     * 短信验证码登录
     */
    @PostMapping("/sms/login")
    public ResponseEntity<?> smsLogin(@RequestBody Map<String, String> request) {
        String phone = request.get("phone");
        String code = request.get("code");

        if (phone == null || phone.isBlank() || code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "手机号和验证码不能为空"));
        }

        if (!smsVerificationService.verifyCode(phone, code)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "验证码错误或已过期"));
        }

        AuthResponse authResponse = authService.smsLogin(phone);
        smsVerificationService.clearPhone(phone);
        log.info("SMS login successful for phone: {}", maskPhone(phone));
        return ResponseEntity.ok(authResponse);
    }

    /**
     * 短信验证码注册
     */
    @PostMapping("/sms/register")
    public ResponseEntity<?> smsRegister(@RequestBody Map<String, String> request) {
        String phone = request.get("phone");
        String code = request.get("code");
        String username = request.get("username");
        String displayName = request.get("displayName");
        String password = request.get("password");

        if (phone == null || phone.isBlank() || code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "手机号和验证码不能为空"));
        }

        if (!smsVerificationService.verifyCode(phone, code)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "验证码错误或已过期"));
        }

        RegisterRequest registerRequest = RegisterRequest.builder()
                .username(username != null ? username : phone)
                .email(phone + "@sms.nebulamind.local")
                .password(password != null ? password : "SmsUser@2024")
                .displayName(displayName != null ? displayName : "用户" + phone.substring(phone.length() - 4))
                .build();

        AuthResponse authResponse = authService.register(registerRequest);
        smsVerificationService.clearPhone(phone);
        log.info("SMS registration successful for phone: {}", maskPhone(phone));
        return ResponseEntity.ok(authResponse);
    }

    /**
     * 修改密码
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        String email = authentication.getName();
        authService.changePassword(email, request.getCurrentPassword(), request.getNewPassword());
        log.info("Password changed for user: {}", email);
        return ResponseEntity.ok(Map.of("success", true, "message", "密码修改成功"));
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
