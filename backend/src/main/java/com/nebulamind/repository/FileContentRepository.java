package com.nebulamind.repository;

import com.nebulamind.entity.FileContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FileContentRepository extends JpaRepository<FileContent, UUID> {

    List<FileContent> findByFileId(UUID fileId);

    void deleteByFileId(UUID fileId);

    long countByFileId(UUID fileId);
}
