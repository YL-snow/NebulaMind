package com.nebulamind.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信验证码服务
 *
 * 支持两种模式：
 *   - dev: 验证码打印到日志，固定为 123456
 *   - prod: 预留真实短信网关接口（需对接阿里云/腾讯云短信服务）
 *
 * 安全限制：
 *   - 验证码 6 位数字
 *   - 有效期 5 分钟
 *   - 同一手机号 60 秒内只能发送一次
 *   - 最多验证 3 次失败后失效
 */
@Slf4j
@Service
public class SmsVerificationService {

    @Value("${nebulamind.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${nebulamind.sms.provider:mock}")
    private String smsProvider;

    // 内存存储（生产环境应使用 Redis）
    private final ConcurrentHashMap<String, SmsCode> codeStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> sendTimestamps = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private static final int CODE_LENGTH = 6;
    private static final int CODE_EXPIRE_SECONDS = 300;  // 5 分钟
    private static final int RESEND_COOLDOWN_SECONDS = 60; // 60 秒冷却
    private static final int MAX_ATTEMPTS = 3;

    /**
     * 发送短信验证码
     *
     * @param phone 手机号
     * @return 是否发送成功
     */
    public boolean sendCode(String phone) {
        if (!smsEnabled) {
            log.info("SMS service is disabled. Skipping code send for: {}", maskPhone(phone));
            return false;
        }

        // 冷却检查
        Long lastSend = sendTimestamps.get(phone);
        long now = Instant.now().getEpochSecond();
        if (lastSend != null && (now - lastSend) < RESEND_COOLDOWN_SECONDS) {
            log.warn("SMS resend too frequent for: {}", maskPhone(phone));
            return false;
        }

        String code = generateCode();
        SmsCode smsCode = new SmsCode(code, now + CODE_EXPIRE_SECONDS, 0);
        codeStore.put(phone, smsCode);
        sendTimestamps.put(phone, now);

        if ("mock".equalsIgnoreCase(smsProvider)) {
            log.info("=== SMS Verification Code ===");
            log.info("  Phone: {}", maskPhone(phone));
            log.info("  Code : {}", code);
            log.info("  Valid: {} minutes", CODE_EXPIRE_SECONDS / 60);
            log.info("==============================");
        } else {
            // TODO: 对接真实短信网关（阿里云/腾讯云）
            sendViaProvider(phone, code);
        }

        log.info("SMS code sent to: {}", maskPhone(phone));
        return true;
    }

    /**
     * 验证短信验证码
     *
     * @param phone 手机号
     * @param code  用户输入的验证码
     * @return 是否验证成功
     */
    public boolean verifyCode(String phone, String code) {
        SmsCode smsCode = codeStore.get(phone);
        if (smsCode == null) {
            log.warn("No SMS code found for: {}", maskPhone(phone));
            return false;
        }

        // 检查过期
        long now = Instant.now().getEpochSecond();
        if (now > smsCode.expiresAt) {
            codeStore.remove(phone);
            log.warn("SMS code expired for: {}", maskPhone(phone));
            return false;
        }

        // 检查尝试次数
        if (smsCode.attempts >= MAX_ATTEMPTS) {
            codeStore.remove(phone);
            log.warn("SMS code max attempts reached for: {}", maskPhone(phone));
            return false;
        }

        smsCode.attempts++;

        // 验证码比对（开发模式下也接受固定码）
        boolean valid = smsCode.code.equals(code)
                || ("mock".equalsIgnoreCase(smsProvider) && "123456".equals(code));

        if (valid) {
            codeStore.remove(phone);
            sendTimestamps.remove(phone);
            log.info("SMS code verified for: {}", maskPhone(phone));
            return true;
        }

        log.warn("Invalid SMS code for: {}, attempts: {}/{}", maskPhone(phone), smsCode.attempts, MAX_ATTEMPTS);

        // 达到最大尝试次数后清除
        if (smsCode.attempts >= MAX_ATTEMPTS) {
            codeStore.remove(phone);
        }

        return false;
    }

    /**
     * 清除手机号的所有验证状态（用于成功注册后）
     */
    public void clearPhone(String phone) {
        codeStore.remove(phone);
        sendTimestamps.remove(phone);
    }

    // ---- 内部实现 ----

    private String generateCode() {
        int code = random.nextInt(900000) + 100000; // 100000 ~ 999999
        return String.valueOf(code);
    }

    private void sendViaProvider(String phone, String code) {
        // TODO: 对接真实短信网关
        // 示例（阿里云）:
        //   DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou", accessKeyId, accessKeySecret);
        //   IAcsClient client = new DefaultAcsClient(profile);
        //   CommonRequest request = new CommonRequest();
        //   request.setSysMethod(MethodType.POST);
        //   request.setSysDomain("dysmsapi.aliyuncs.com");
        //   request.setSysVersion("2017-05-25");
        //   request.setSysAction("SendSms");
        //   request.putQueryParameter("PhoneNumbers", phone);
        //   request.putQueryParameter("SignName", "NebulaMind");
        //   request.putQueryParameter("TemplateCode", "SMS_XXXXXX");
        //   request.putQueryParameter("TemplateParam", "{\"code\":\"" + code + "\"}");
        //   client.getCommonResponse(request);
        log.info("SMS sent via {}: code={} to phone={}", smsProvider, code, maskPhone(phone));
    }

    /**
     * 手机号脱敏显示
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    // ---- 内部类 ----
    private static class SmsCode {
        final String code;
        final long expiresAt;  // epoch seconds
        int attempts;

        SmsCode(String code, long expiresAt, int attempts) {
            this.code = code;
            this.expiresAt = expiresAt;
            this.attempts = attempts;
        }
    }
}
