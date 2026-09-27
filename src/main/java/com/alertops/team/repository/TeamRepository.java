package com.alertops.team.repository;

import com.alertops.team.model.Team;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

}
