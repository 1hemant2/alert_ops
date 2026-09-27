package com.alertops.team.repository;

import com.alertops.team.model.Invite;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, Long> {
    Optional<Invite> findByToken(UUID token);

    boolean existsByTeamIdAndEmailIgnoreCaseAndStatus(UUID teamId, String email, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT invite FROM Invite invite WHERE invite.token = :token")
    Optional<Invite> findByTokenForUpdate(@Param("token") UUID token);
}
