package com.nebulamind.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 50)
    @Convert(converter = com.nebulamind.entity.converter.AuditLogActionConverter.class)
    private Action action;

    @Column(name = "resource_type", nullable = false, length = 50)
    @Convert(converter = com.nebulamind.entity.converter.AuditLogResourceTypeConverter.class)
    private ResourceType resourceType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String details;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum Action {
        LOGIN, LOGOUT, UPLOAD, DOWNLOAD, DELETE,
        SHARE, REVOKE_SHARE, ENCRYPT, DECRYPT,
        VERSION_RESTORE, CLASSIFY, SEARCH, QA,
        USER_CREATE, USER_DISABLE, ROLE_CHANGE
    }

    public enum ResourceType {
        FILE, PERMISSION, USER, SYSTEM
    }
}
