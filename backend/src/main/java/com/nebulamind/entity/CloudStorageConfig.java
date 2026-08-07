package com.nebulamind.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 云存储配置实体。
 * 用户可在此配置对接不同的云存储/云盘服务，
 * 如 S3 兼容存储、联通云盘等。
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cloud_storage_configs")
public class CloudStorageConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 关联用户 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 配置名称（用户自定义，如"我的联通云盘"） */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 存储类型：
     * - S3: S3 兼容对象存储（MinIO、AWS S3 等）
     * - UNICOM: 联通云盘
     */
    @Column(name = "provider_type", nullable = false, length = 20)
    private String providerType;

    /** API 端点 URL（S3 的 endpoint，或云盘的 base URL） */
    @Column(name = "endpoint_url", length = 500)
    private String endpointUrl;

    /** Access Key / App ID */
    @Column(name = "access_key", length = 256)
    private String accessKey;

    /** Secret Key / App Secret（AES 加密存储） */
    @Column(name = "secret_key", length = 512)
    private String secretKey;

    /** Bucket 名称 / 存储区域 */
    @Column(name = "bucket_name", length = 100)
    private String bucketName;

    /** 区域（S3 region） */
    @Column(length = 50)
    private String region;

    /** OAuth2 重定向 URI（云盘类型时使用） */
    @Column(name = "redirect_uri", length = 500)
    private String redirectUri;

    /** 是否启用此配置 */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    /** 上次连接测试是否成功 */
    @Column(name = "last_test_success")
    private Boolean lastTestSuccess;

    /** 上次测试时间 */
    @Column(name = "last_test_at")
    private LocalDateTime lastTestAt;

    /** 额外配置（JSON 格式） */
    @Column(columnDefinition = "text")
    private String extraConfig;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
