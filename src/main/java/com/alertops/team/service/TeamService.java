package com.alertops.team.service;

import com.alertops.permissions.GrantPermission;
import com.alertops.permissions.Permission;
import com.alertops.permissions.Role;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import com.alertops.security.JwtUtil;
import com.alertops.team.dto.TeamListItemDto;
import com.alertops.team.dto.TeamMemberResponseDto;
import com.alertops.team.dto.TeamResDto;
import com.alertops.team.model.Team;
import com.alertops.team.model.TeamMember;
import com.alertops.team.repository.TeamMemberRepository;
import com.alertops.team.repository.TeamRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TeamService {

    GrantPermission grantPermission;
    TeamRepository teamRepository;
    JwtUtil jwtUtil;
    TeamMemberRepository teamMemberRepository;

    TeamService(GrantPermission grantPermission,
                TeamRepository teamRepository,
                JwtUtil jwtUtil,
                TeamMemberRepository teamMemberRepository
                ) {
        this.grantPermission = grantPermission;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public TeamResDto createTeam(String teamName) {

        try {
            AuthContext ctx = AuthContextHolder.get();

            if (ctx == null)  throw new RuntimeException("Unauthenticated");

            Team team = new Team();
            team.setName(teamName);
            Team newTeam = teamRepository.save(team);

            TeamMember teamMember = new TeamMember();
            teamMember.setTeamId(newTeam.getId());
            teamMember.setRole(String.valueOf(Role.TEAM_OWNER));
            teamMember.setUserId(ctx.getUserId());
            teamMemberRepository.save(teamMember);

            TeamResDto res = new TeamResDto();
            res.setTeamId(team.getId());
            res.setTeamName(teamName);
            res.setUserId(ctx.getUserId());
            res.setRole(String.valueOf(Role.TEAM_OWNER));
            return  res;
        } catch (RuntimeException e) {
            throw  e;
        }
    }


    public Map<String, Object> selectTeam(UUID teamId) {

        try {
            AuthContext ctx = AuthContextHolder.get();

            if (ctx == null)  throw new AccessDeniedException("Authentication is required.");
            TeamMember teamMember = teamMemberRepository.findTeamMemeber(ctx.getUserId(), teamId);
            if (teamMember == null) {
                throw new AccessDeniedException("You are not a member of this team.");
            }
            grantPermission.grant(teamMember.getRole(), String.valueOf(Permission.SELECT_TEAM));
            Map<String, Object> obj = new HashMap<> ();
            obj.put("email", ctx.getEmail());
            obj.put("teamId", teamId);
            obj.put("role", teamMember.getRole());

            return Map.of("token", jwtUtil.generateToken(ctx.getUserId().toString(), obj, 60000));
        } catch (RuntimeException e) {
            throw  e;
        }
    }

    /**
     * @description: change the user role or revoke it by setting none
     */
    public void updateUserRole() {

    }

    public List<TeamListItemDto> getUserTeams() {
        try {
            AuthContext ctx = AuthContextHolder.get();

            if (ctx == null)  throw new RuntimeException("Unauthenticated");

            return teamMemberRepository.findTeamListByUserId(ctx.getUserId()).stream()
                    .map(team -> new TeamListItemDto(team.getId(), team.getName(), team.getRole()))
                    .toList();
        } catch (RuntimeException e) {
            throw  e;
        }
    }

    public List<TeamMemberResponseDto> getTeamMembers() {
        AuthContext ctx = AuthContextHolder.get();
        if (ctx == null || ctx.getTeamId() == null) {
            throw new AccessDeniedException("Select a team before viewing its members.");
        }
        return teamMemberRepository.findMembersByTeamId(ctx.getTeamId()).stream()
                .map(member -> new TeamMemberResponseDto(
                        member.getMemberId(),
                        member.getUserId(),
                        member.getName(),
                        member.getEmail(),
                        member.getRole()))
                .toList();
    }
}
