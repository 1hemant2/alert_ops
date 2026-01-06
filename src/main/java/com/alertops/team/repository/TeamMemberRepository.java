package com.alertops.team.repository;

import com.alertops.team.model.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    @Query("SELECT tm FROM TeamMember tm WHERE tm.userId = :userId AND tm.teamId = :teamId")
    TeamMember findTeamMemeber(UUID userId, UUID teamId);
}
