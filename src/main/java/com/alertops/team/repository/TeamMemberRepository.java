package com.alertops.team.repository;

import com.alertops.team.model.Team;
import com.alertops.team.model.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {
    @Query("SELECT tm FROM TeamMember tm WHERE tm.userId = :userId AND tm.teamId = :teamId")
    TeamMember findTeamMemeber(UUID userId, UUID teamId);

    boolean existsByUserIdAndTeamId(UUID userId, UUID teamId);

    @Query("SELECT t FROM Team t JOIN TeamMember tm ON t.id = tm.teamId WHERE tm.userId = :userId")
    List<Team> findByUserId(UUID userId);

    @Query(value = """
            SELECT t.id AS id, t.name AS name, tm.role AS role
            FROM team t
            JOIN team_member tm ON tm.team_id = t.id
            WHERE tm.user_id = :userId
            ORDER BY t.name
            """, nativeQuery = true)
    List<TeamListItemProjection> findTeamListByUserId(UUID userId);

    @Query(value = """
            SELECT tm.id AS "memberId", u.id AS "userId", u.name AS name,
                   u.email AS email, tm.role AS role
            FROM team_member tm
            JOIN users u ON u.id = tm.user_id
            WHERE tm.team_id = :teamId
            ORDER BY u.name
            """, nativeQuery = true)
    List<TeamMemberProjection> findMembersByTeamId(UUID teamId);
}
