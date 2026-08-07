package com.nebulamind.repository;

import com.nebulamind.entity.SemanticIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SemanticIndexRepository extends JpaRepository<SemanticIndex, UUID> {

    List<SemanticIndex> findByFileId(UUID fileId);

    void deleteByFileId(UUID fileId);

    long countByFileId(UUID fileId);
}
