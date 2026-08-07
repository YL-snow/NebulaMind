package com.nebulamind.repository;

import com.nebulamind.entity.File;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileRepository extends JpaRepository<File, UUID> {

    Page<File> findByUserId(UUID userId, Pageable pageable);

    Page<File> findByUserIdAndStatus(UUID userId, File.FileStatus status, Pageable pageable);

    Optional<File> findByIdAndUserId(UUID id, UUID userId);

    List<File> findByHash(String hash);

    List<File> findByUserIdAndParentId(UUID userId, UUID parentId);

    long countByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, File.FileStatus status);

    Optional<File> findByPath(String path);

    List<File> findByUserIdAndPathContaining(UUID userId, String path);

    Optional<File> findByCloudDriveFileId(String cloudDriveFileId);
}
