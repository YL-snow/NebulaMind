package com.nebulamind.repository;

import com.nebulamind.entity.CloudStorageConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CloudStorageConfigRepository extends JpaRepository<CloudStorageConfig, UUID> {

    List<CloudStorageConfig> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<CloudStorageConfig> findByUserIdAndIsActiveTrue(UUID userId);
}
