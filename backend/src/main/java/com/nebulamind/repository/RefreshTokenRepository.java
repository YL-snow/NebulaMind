package com.nebulamind.repository;

import com.nebulamind.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserId(UUID userId);

    List<RefreshToken> findByRevokedFalseAndExpiresAtAfter(LocalDateTime dateTime);

    void deleteByUserId(UUID userId);

    void deleteByRevokedTrue();

    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}
