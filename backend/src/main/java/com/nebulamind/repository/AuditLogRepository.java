package com.nebulamind.repository;

import com.nebulamind.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByUserId(UUID userId, Pageable pageable);

    Page<AuditLog> findByAction(AuditLog.Action action, Pageable pageable);

    Page<AuditLog> findByResourceType(AuditLog.ResourceType resourceType, Pageable pageable);

    List<AuditLog> findByUserIdAndAction(UUID userId, AuditLog.Action action);

    List<AuditLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByUserIdAndCreatedAtBetween(UUID userId, LocalDateTime start, LocalDateTime end);
}
