package com.alertops.team.service;

import com.alertops.caching.Intent;
import com.alertops.caching.IntentCache;
import com.alertops.caching.IntentType;
import com.alertops.global.GrantPermission;
import com.alertops.global.Permission;
import com.alertops.global.Role;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import com.alertops.security.JwtUtil;
import com.alertops.team.constant.TeamConstant;
import com.alertops.team.dto.GetTeamReqDto;
import com.alertops.team.dto.InviteDtoReq;
import com.alertops.team.dto.TeamResDto;
import com.alertops.team.model.Invite;
import com.alertops.team.model.Team;
import com.alertops.team.model.TeamMember;
import com.alertops.team.repository.InviteRepository;
import com.alertops.team.repository.TeamMemberRepository;
import com.alertops.team.repository.TeamRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
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
    InviteRepository inviteRepository;
    IntentCache intentCache;

    TeamService(GrantPermission grantPermission,
                TeamRepository teamRepository,
                JwtUtil jwtUtil,
                TeamMemberRepository teamMemberRepository,
                InviteRepository inviteRepository,
                IntentCache intentCache
                ) {
        this.grantPermission = grantPermission;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.jwtUtil = jwtUtil;
        this.inviteRepository = inviteRepository;
        this.intentCache = intentCache;
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

            if (ctx == null)  throw new RuntimeException("Unauthenticated");
            System.out.println("Selecting team: " + teamId + " for user: " + ctx.getUserId());
            TeamMember teamMember = teamMemberRepository.findTeamMemeber(ctx.getUserId(), teamId);
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
     * @description: Invite user to join the team with the specific role.
     *  - take userName, userEmail, role
     *  - Generate random token save it in the invite model.
     *  - send invite on email with token embaded inside the url.
     *  - send the response as user invited successfully
     */
    public void inviteUser(InviteDtoReq req) {
        try {
            AuthContext ctx = AuthContextHolder.get();
            Invite invite = new Invite();
            if(ctx.getTeamId() == null) {
                throw new RuntimeException("Please select a team first");
            }   
            invite.setEmail(req.getEmail());
            invite.setRole(req.getRole());;
            invite.setTtl(Duration.ofMinutes(req.getTtl()));
            invite.setTeamId(ctx.getTeamId());
            invite.setStatus(String.valueOf(TeamConstant.INVITED));
            inviteRepository.save(invite);

            System.out.println("email have been sent to user with token -->" + invite.getToken()); // replace this with acctual email
        } catch (RuntimeException e) {
            throw  e;
        }
    }

    /**
     * @description: Make user to join the team
     */
    public Map<String, Object> joinTeam(UUID token) {
        try {
            AuthContext ctx = AuthContextHolder.get();

            if (ctx == null) {
                validateToken(token, null);

                UUID intentId = UUID.randomUUID();

                intentCache.put(intentId, new Intent(IntentType.JOIN_TEAM));

                return Map.of(
                        "action", "LOGIN_REQUIRED",
                        "path", "/api/v1/auth/login",
                        "intentId", intentId
                );
            }

            if (ctx.getEmail() == null) {
                throw new RuntimeException("Something went wrong");
            }

            Invite invite = validateToken(token, ctx.getEmail());
            this.jointTeamHelper(
                   invite.getTeamId(),
                   ctx.getUserId(),
                    invite.getRole()
            );
            invite.setStatus(String.valueOf(TeamConstant.ACCEPTED));
            inviteRepository.save(invite);
            return Map.of(
                    "action", "REDIRECT",
                    "path", "/api/v1/team/select"
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    Invite validateToken(UUID token, String email) {
        try {
            Invite invite = inviteRepository.findByToken(token);

            System.out.println("invite found: " + invite.getEmail());
            System.out.println("email provided: " + email);

            if(email != null && !email.equals(invite.getEmail())) {
                throw  new Error("email is invalid");
            }

            if(!invite.getStatus().equals(String.valueOf(TeamConstant.INVITED))) {
                throw  new Error("token is already consumed");
            }

            Instant expiresAt = invite.getCreatedAt().plus(invite.getTtl());

            if (Instant.now().isAfter(expiresAt)) {
                throw new RuntimeException("Invitation expired");
            }

            return  invite;
        } catch (Exception e) {
            throw new   RuntimeException(e);
        }
    }

    void jointTeamHelper(UUID teamId, UUID userId, String role) {
        try {
            TeamMember teamMember = new TeamMember();
            teamMember.setRole(role);
            teamMember.setUserId(userId);
            teamMember.setTeamId(teamId);
            teamMemberRepository.save(teamMember);
        } catch (Exception e) {
            throw  new RuntimeException(e);
        }
    }

    /**
     * @description: change the user role or revoke it by setting none
     */
    public void updateUserRole() {

    }

    public List<Team> getUserTeams() {
        try {
            AuthContext ctx = AuthContextHolder.get();

            if (ctx == null)  throw new RuntimeException("Unauthenticated");

            return teamMemberRepository.findByUserId(ctx.getUserId());
        } catch (RuntimeException e) {
            throw  e;
        }
    }
}




