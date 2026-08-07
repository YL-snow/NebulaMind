package com.nebulamind.repository;

import com.nebulamind.entity.AiCallLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AiCallLogRepository extends JpaRepository<AiCallLog, UUID> {

    Page<AiCallLog> findByUserId(UUID userId, Pageable pageable);

    Page<AiCallLog> findByModule(String module, Pageable pageable);

    List<AiCallLog> findByRequestId(UUID requestId);

    List<AiCallLog> findByFileId(UUID fileId);

    List<AiCallLog> findBySuccessFalse();

    long countByUserIdAndCreatedAtBetween(UUID userId, LocalDateTime start, LocalDateTime end);
}
