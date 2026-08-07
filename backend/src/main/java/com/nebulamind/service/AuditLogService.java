package com.nebulamind.service;

import com.nebulamind.entity.AuditLog;
import com.nebulamind.entity.User;
import com.nebulamind.repository.AuditLogRepository;
import com.nebulamind.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安全审计服务
 *
 * 功能：
 *   - 操作日志记录：记录所有敏感操作（登录/上传/下载/删除/分享/加密/解密）
 *   - 安全事件监控：检测异常访问、高频调用
 *   - 告警机制：实时告警 + 日志记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    // 内存中的事件计数器（用于高频检测）
    private final Map<String, List<Long>> accessTimeline = new ConcurrentHashMap<>();
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();

    // 告警监听器列表
    private final List<AlertListener> alertListeners = new ArrayList<>();

    private static final int HIGH_FREQ_THRESHOLD = 100;  // 每分钟超过100次
    private static final int FAILED_LOGIN_THRESHOLD = 5;  // 5次失败登录
    private static final int SUSPICIOUS_IP_THRESHOLD = 50; // 单IP每分钟50次

    // ==================== 操作日志记录 ====================

    /**
     * 记录操作日志
     *
     * @param userId       操作用户ID（可为null）
     * @param action       操作类型
     * @param resourceType 资源类型
     * @param resourceId   资源ID
     * @param details      详细信息（JSON格式）
     * @param request      HTTP请求（用于提取IP和User-Agent）
     */
    @Transactional
    public AuditLog log(UUID userId, AuditLog.Action action,
                         AuditLog.ResourceType resourceType, String resourceId,
                         String details, HttpServletRequest request) {
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }

        String ipAddress = extractIp(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : null;
        if (userAgent != null && userAgent.length() > 500) {
            userAgent = userAgent.substring(0, 500);
        }

        AuditLog auditLog = AuditLog.builder()
                .user(user)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .details(details)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        auditLog = auditLogRepository.save(auditLog);

        // 触发安全监控检查
        checkSecurityPatterns(userId, ipAddress, action);

        return auditLog;
    }

    /**
     * 便捷方法：不依赖 HttpServletRequest
     */
    @Transactional
    public AuditLog logSimple(UUID userId, AuditLog.Action action,
                               AuditLog.ResourceType resourceType, String resourceId,
                               String details, String ipAddress) {
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }

        AuditLog auditLog = AuditLog.builder()
                .user(user)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .details(details)
                .ipAddress(ipAddress != null ? ipAddress : "0.0.0.0")
                .userAgent(null)
                .build();

        auditLog = auditLogRepository.save(auditLog);
        checkSecurityPatterns(userId, ipAddress, action);

        return auditLog;
    }

    // ==================== 查询 ====================

    public Page<AuditLog> getUserLogs(UUID userId, Pageable pageable) {
        return auditLogRepository.findByUserId(userId, pageable);
    }

    public Page<AuditLog> getLogsByAction(AuditLog.Action action, Pageable pageable) {
        return auditLogRepository.findByAction(action, pageable);
    }

    public Page<AuditLog> getLogsByResourceType(AuditLog.ResourceType resourceType, Pageable pageable) {
        return auditLogRepository.findByResourceType(resourceType, pageable);
    }

    public List<AuditLog> getLogsByTimeRange(LocalDateTime start, LocalDateTime end) {
        return auditLogRepository.findByCreatedAtBetween(start, end);
    }

    /**
     * 获取最近N条日志
     */
    public List<AuditLog> getRecentLogs(int n) {
        // 使用分页获取最新日志
        Page<AuditLog> page = auditLogRepository.findAll(
                Pageable.ofSize(n).withPage(0));
        return page.getContent();
    }

    // ==================== 安全事件监控 ====================

    /**
     * 检查安全访问模式
     */
    private void checkSecurityPatterns(UUID userId, String ipAddress, AuditLog.Action action) {
        long now = System.currentTimeMillis();

        // 1. 按用户检查访问频率
        if (userId != null) {
            String userKey = "user:" + userId;
            int userCount = countRecentAccesses(userKey, now);
            if (userCount > HIGH_FREQ_THRESHOLD) {
                String message = String.format(
                        "高频访问告警: 用户 %s 在最近1分钟内发起了 %d 次请求",
                        userId, userCount);
                triggerAlert("HIGH_FREQUENCY", message, userId.toString(), "USER");
            }
        }

        // 2. 按IP检查访问频率
        if (ipAddress != null) {
            String ipKey = "ip:" + ipAddress;
            int ipCount = countRecentAccesses(ipKey, now);
            if (ipCount > SUSPICIOUS_IP_THRESHOLD) {
                String message = String.format(
                        "可疑IP告警: IP %s 在最近1分钟内发起了 %d 次请求",
                        ipAddress, ipCount);
                triggerAlert("SUSPICIOUS_IP", message, ipAddress, "IP");
            }
        }

        // 3. 检查失败登录尝试
        if (action == AuditLog.Action.LOGIN && userId == null) {
            // 登录失败（无法确定用户）
            String failKey = "failed:" + (ipAddress != null ? ipAddress : "unknown");
            failedAttempts.merge(failKey, 1, Integer::sum);
            int fails = failedAttempts.get(failKey);
            if (fails >= FAILED_LOGIN_THRESHOLD) {
                String message = String.format(
                        "暴力破解告警: IP %s 在短时间内有 %d 次登录失败",
                        ipAddress, fails);
                triggerAlert("BRUTE_FORCE", message, ipAddress, "SECURITY");
            }
        } else if (action == AuditLog.Action.LOGIN && userId != null) {
            // 登录成功，重置该IP的失败计数
            String failKey = "failed:" + (ipAddress != null ? ipAddress : "unknown");
            failedAttempts.remove(failKey);
        }
    }

    private int countRecentAccesses(String key, long now) {
        List<Long> timestamps = accessTimeline.computeIfAbsent(key, k -> new ArrayList<>());
        // 清理超过1分钟的记录
        timestamps.removeIf(ts -> now - ts > 60_000);
        timestamps.add(now);
        return timestamps.size();
    }

    // ==================== 告警机制 ====================

    /**
     * 注册告警监听器
     */
    public void registerAlertListener(AlertListener listener) {
        alertListeners.add(listener);
        log.info("Alert listener registered: {}", listener.getClass().getSimpleName());
    }

    /**
     * 触发告警
     */
    private void triggerAlert(String alertType, String message, String source, String severity) {
        log.warn("[ALERT][{}][{}] {} (source: {})", severity, alertType, message, source);

        AlertEvent event = new AlertEvent(alertType, message, source, severity, LocalDateTime.now());

        for (AlertListener listener : alertListeners) {
            try {
                listener.onAlert(event);
            } catch (Exception e) {
                log.error("Alert listener error: {}", e.getMessage());
            }
        }
    }

    // ---- 定时任务 ----

    /**
     * 定期清理过期访问记录（每分钟执行）
     */
    @Scheduled(fixedRate = 60_000)
    public void cleanupAccessTimeline() {
        long now = System.currentTimeMillis();
        accessTimeline.forEach((key, timestamps) -> {
            timestamps.removeIf(ts -> now - ts > 60_000);
        });
        // 清理空列表
        accessTimeline.entrySet().removeIf(e -> e.getValue().isEmpty());

        // 重置过期失败计数（超过30分钟）
        failedAttempts.clear(); // 简化处理
    }

    // ==================== 工具方法 ====================

    private String extractIp(HttpServletRequest request) {
        if (request == null) return "0.0.0.0";

        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip;
        }

        return request.getRemoteAddr();
    }

    // ==================== 内部类 ====================

    public interface AlertListener {
        void onAlert(AlertEvent event);
    }

    public record AlertEvent(String alertType, String message, String source,
                             String severity, LocalDateTime timestamp) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("alertType", alertType);
            map.put("message", message);
            map.put("source", source);
            map.put("severity", severity);
            map.put("timestamp", timestamp.toString());
            return map;
        }
    }

    /**
     * 默认告警监听器 - 日志输出 + 控制台打印
     */
    @org.springframework.stereotype.Component
    public static class LoggingAlertListener implements AlertListener {
        @Override
        public void onAlert(AlertEvent event) {
            // 高严重度告警特殊标记
            if ("HIGH".equalsIgnoreCase(event.severity()) || "CRITICAL".equalsIgnoreCase(event.severity())) {
                log.error("""
                    ╔══════════════════════════════════════════╗
                    ║  [SECURITY ALERT] {}
                    ║  Type    : {}
                    ║  Source  : {}
                    ║  Time    : {}
                    ╚══════════════════════════════════════════╝""",
                        event.alertType(), event.message(), event.source(), event.timestamp());
            }
        }
    }
}
