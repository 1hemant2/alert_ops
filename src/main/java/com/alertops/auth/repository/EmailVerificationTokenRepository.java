package com.alertops.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.alertops.auth.model.EmailVerificationToken;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT verificationToken FROM EmailVerificationToken verificationToken WHERE verificationToken.tokenHash = :tokenHash")
    Optional<EmailVerificationToken> findAndLockByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            UPDATE EmailVerificationToken verificationToken
            SET verificationToken.consumedAt = :consumedAt
            WHERE verificationToken.userId = :userId AND verificationToken.consumedAt IS NULL
            """)
    int invalidateUnusedTokensForUser(
            @Param("userId") UUID userId,
            @Param("consumedAt") Instant consumedAt);
}
