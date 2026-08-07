package com.nebulamind.repository;

import com.nebulamind.entity.FileVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {

    List<FileVersion> findByFileIdOrderByVersionNumberDesc(UUID fileId);

    Page<FileVersion> findByFileId(UUID fileId, Pageable pageable);

    void deleteByFileId(UUID fileId);

    long countByFileId(UUID fileId);
}
