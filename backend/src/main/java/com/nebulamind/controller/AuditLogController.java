package com.nebulamind.controller;

import com.nebulamind.entity.AuditLog;
import com.nebulamind.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 安全审计 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 查询用户的操作日志
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserLogs(
            @PathVariable UUID userId,
            Pageable pageable) {
        Page<AuditLog> logs = auditLogService.getUserLogs(userId, pageable);
        return ResponseEntity.ok(logs);
    }

    /**
     * 按操作类型查询日志
     */
    @GetMapping("/action/{action}")
    public ResponseEntity<?> getLogsByAction(
            @PathVariable String action,
            Pageable pageable) {
        try {
            AuditLog.Action auditAction = AuditLog.Action.valueOf(action.toUpperCase());
            Page<AuditLog> logs = auditLogService.getLogsByAction(auditAction, pageable);
            return ResponseEntity.ok(logs);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的操作类型: " + action));
        }
    }

    /**
     * 按资源类型查询日志
     */
    @GetMapping("/resource/{resourceType}")
    public ResponseEntity<?> getLogsByResourceType(
            @PathVariable String resourceType,
            Pageable pageable) {
        try {
            AuditLog.ResourceType type = AuditLog.ResourceType.valueOf(resourceType.toUpperCase());
            Page<AuditLog> logs = auditLogService.getLogsByResourceType(type, pageable);
            return ResponseEntity.ok(logs);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的资源类型: " + resourceType));
        }
    }

    /**
     * 按时间范围查询日志
     */
    @GetMapping("/timerange")
    public ResponseEntity<?> getLogsByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        List<AuditLog> logs = auditLogService.getLogsByTimeRange(start, end);
        return ResponseEntity.ok(Map.of(
                "start", start.toString(),
                "end", end.toString(),
                "count", logs.size(),
                "logs", logs
        ));
    }

    /**
     * 获取最近N条日志
     */
    @GetMapping("/recent")
    public ResponseEntity<?> getRecentLogs(@RequestParam(defaultValue = "50") int n) {
        List<AuditLog> logs = auditLogService.getRecentLogs(Math.min(n, 200));
        return ResponseEntity.ok(logs);
    }

    /**
     * 获取安全告警统计
     */
    @GetMapping("/alerts/summary")
    public ResponseEntity<?> getAlertSummary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24h = now.minusHours(24);

        List<AuditLog> recentLogs = auditLogService.getLogsByTimeRange(last24h, now);

        long loginFailures = recentLogs.stream()
                .filter(l -> l.getAction() == AuditLog.Action.LOGIN)
                .count();

        long fileDeletions = recentLogs.stream()
                .filter(l -> l.getAction() == AuditLog.Action.DELETE)
                .count();

        long permissionChanges = recentLogs.stream()
                .filter(l -> l.getAction() == AuditLog.Action.SHARE
                        || l.getAction() == AuditLog.Action.REVOKE_SHARE)
                .count();

        return ResponseEntity.ok(Map.of(
                "period", "24h",
                "totalEvents", recentLogs.size(),
                "loginAttempts", loginFailures,
                "fileDeletions", fileDeletions,
                "permissionChanges", permissionChanges,
                "generatedAt", now.toString()
        ));
    }

    /**
     * 导出审计日志（简化版）
     */
    @GetMapping("/export")
    public ResponseEntity<?> exportLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        List<AuditLog> logs = auditLogService.getLogsByTimeRange(start, end);

        // 格式化为可导出的结构
        List<Map<String, Object>> exportData = logs.stream().map(log -> {
            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("id", log.getId().toString());
            entry.put("userId", log.getUser() != null ? log.getUser().getId().toString() : null);
            entry.put("username", log.getUser() != null ? log.getUser().getUsername() : "anonymous");
            entry.put("action", log.getAction().name());
            entry.put("resourceType", log.getResourceType().name());
            entry.put("resourceId", log.getResourceId());
            entry.put("details", log.getDetails());
            entry.put("ipAddress", log.getIpAddress());
            entry.put("userAgent", log.getUserAgent());
            entry.put("createdAt", log.getCreatedAt().toString());
            return entry;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "exportTime", LocalDateTime.now().toString(),
                "period", Map.of("start", start.toString(), "end", end.toString()),
                "totalCount", exportData.size(),
                "records", exportData
        ));
    }
}
