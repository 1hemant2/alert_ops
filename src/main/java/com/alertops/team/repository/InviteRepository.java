package com.alertops.team.repository;

import com.alertops.team.model.Invite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, Long> {
    Invite findByToken(UUID token);
}
