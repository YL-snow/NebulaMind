package com.nebulamind.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "files", indexes = {
        @Index(name = "idx_files_user_id", columnList = "user_id"),
        @Index(name = "idx_files_hash", columnList = "hash"),
        @Index(name = "idx_files_path", columnList = "path"),
        @Index(name = "idx_files_cloud_drive_file_id", columnList = "cloud_drive_file_id"),
        @Index(name = "idx_files_status", columnList = "status"),
        @Index(name = "idx_files_user_status", columnList = "user_id, status"),
        @Index(name = "idx_files_user_updated", columnList = "user_id, updated_at"),
        @Index(name = "idx_files_category", columnList = "category"),
        @Index(name = "idx_files_sensitive_level", columnList = "sensitive_level")
})
public class File implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(nullable = false, length = 1000)
    private String path;

    @Column(nullable = false)
    private Long size;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_type", nullable = false, length = 50)
    private String fileType;

    @Transient
    public String getType() {
        return fileType;
    }

    @Column(nullable = false, length = 64)
    private String hash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @JsonIgnore
    private File parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private FileStatus status = FileStatus.UPLOADING;

    @Column(name = "ai_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AiStatus aiStatus = AiStatus.PENDING;

    @Column(length = 100)
    private String category;

    @Column(columnDefinition = "jsonb")
    private String tags;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "ai_error_message", columnDefinition = "text")
    private String aiErrorMessage;

    @Column(name = "sensitive_level", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SensitiveLevel sensitiveLevel = SensitiveLevel.NORMAL;

    @Column(name = "is_encrypted", nullable = false)
    private Boolean isEncrypted = false;

    @Column(name = "encryption_key_id", length = 100)
    private String encryptionKeyId;

    @Column(name = "cloud_drive_file_id", length = 200)
    private String cloudDriveFileId;

    @Column(nullable = false)
    private Integer version = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum FileStatus {
        UPLOADING, PROCESSING, COMPLETED, FAILED
    }

    public enum AiStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    public enum SensitiveLevel {
        NORMAL, LOW, MEDIUM, HIGH
    }
}
