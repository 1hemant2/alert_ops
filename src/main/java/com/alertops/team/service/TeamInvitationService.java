package com.alertops.team.service;

import com.alertops.auth.model.User;
import com.alertops.auth.repository.UserRepository;
import com.alertops.messaging.TeamInvitationMailer;
import com.alertops.permissions.GrantPermission;
import com.alertops.permissions.Permission;
import com.alertops.permissions.Role;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import com.alertops.team.constant.TeamConstant;
import com.alertops.team.dto.InviteAcceptanceResponse;
import com.alertops.team.dto.InviteDtoReq;
import com.alertops.team.dto.InvitePreviewDto;
import com.alertops.team.dto.InviteResponseDto;
import com.alertops.team.model.Invite;
import com.alertops.team.model.Team;
import com.alertops.team.model.TeamMember;
import com.alertops.team.repository.InviteRepository;
import com.alertops.team.repository.TeamMemberRepository;
import com.alertops.team.repository.TeamRepository;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class TeamInvitationService {
    private static final int MIN_EXPIRY_HOURS = 1;
    private static final int MAX_EXPIRY_HOURS = 168;

    private final GrantPermission grantPermission;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final InviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final TeamInvitationMailer invitationMailer;
    private final String uiBaseUrl;

    public TeamInvitationService(
            GrantPermission grantPermission,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            InviteRepository inviteRepository,
            UserRepository userRepository,
            TeamInvitationMailer invitationMailer,
            @Value("${alertops.ui.base-url}") String uiBaseUrl) {
        this.grantPermission = grantPermission;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
        this.invitationMailer = invitationMailer;
        this.uiBaseUrl = uiBaseUrl == null ? "" : uiBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public InviteResponseDto createInvite(InviteDtoReq request) {
        AuthContext context = AuthContextHolder.get();
        if (context == null || context.getTeamId() == null) {
            throw new AccessDeniedException("Select a team before inviting a member.");
        }
        grantPermission.grant(context.getRole(), Permission.INVITE_USER.name());

        String email = normalizeAndValidateEmail(request == null ? null : request.email());
        Role invitedRole = parseInvitedRole(request.role());
        Role inviterRole = parseInviterRole(context.getRole());
        if (inviterRole == Role.ADMIN && invitedRole != Role.USER) {
            throw new AccessDeniedException("Admins can invite users only.");
        }
        long expiryHours = request.expiresInHours() == null ? 0 : request.expiresInHours();
        if (expiryHours < MIN_EXPIRY_HOURS || expiryHours > MAX_EXPIRY_HOURS) {
            throw new IllegalArgumentException("Invitation expiry must be between 1 and 168 hours.");
        }

        Team team = teamRepository.findById(context.getTeamId())
                .orElseThrow(() -> new InviteNotFoundException());
        User existingUser = userRepository.findByEmailIgnoreCase(email);
        if (existingUser != null && teamMemberRepository.existsByUserIdAndTeamId(existingUser.getId(), team.getId())) {
            throw new InviteConflictException("This person is already a member of the team.");
        }
        if (inviteRepository.existsByTeamIdAndEmailIgnoreCaseAndStatus(
                team.getId(), email, TeamConstant.INVITED.name())) {
            throw new InviteConflictException("An invitation for this email is already pending.");
        }
        if (uiBaseUrl.isBlank()) {
            throw new IllegalStateException("The invitation link URL is not configured.");
        }

        Invite invite = new Invite();
        invite.setToken(UUID.randomUUID());
        invite.setEmail(email);
        invite.setRole(invitedRole.name());
        invite.setTeamId(team.getId());
        invite.setTtl(Duration.ofHours(expiryHours));
        invite.setStatus(TeamConstant.INVITED.name());
        Invite savedInvite = inviteRepository.saveAndFlush(invite);

        String invitationUrl = uiBaseUrl + "/join?token=" + savedInvite.getToken();
        invitationMailer.sendInvitation(savedInvite, team, invitationUrl);

        Instant expiresAt = savedInvite.getCreatedAt().plus(savedInvite.getTtl());
        return new InviteResponseDto(savedInvite.getEmail(), savedInvite.getRole(), team.getName(), expiresAt);
    }

    public InvitePreviewDto preview(UUID token) {
        Invite invite = findInvite(token);
        assertInviteIsPending(invite);
        Team team = teamRepository.findById(invite.getTeamId())
                .orElseThrow(() -> new InviteNotFoundException());
        return new InvitePreviewDto(
                invite.getEmail(),
                invite.getRole(),
                team.getName(),
                invite.getCreatedAt().plus(invite.getTtl()));
    }

    @Transactional
    public InviteAcceptanceResponse accept(UUID token) {
        AuthContext context = AuthContextHolder.get();
        if (context == null || context.getUserId() == null || context.getEmail() == null) {
            throw new AccessDeniedException("Sign in with the invited email address to accept this invitation.");
        }

        Invite invite = inviteRepository.findByTokenForUpdate(token).orElseThrow(InviteNotFoundException::new);
        assertInviteIsPending(invite);
        if (!context.getEmail().trim().equalsIgnoreCase(invite.getEmail())) {
            throw new AccessDeniedException("Sign in with the email address this invitation was sent to.");
        }
        if (teamMemberRepository.existsByUserIdAndTeamId(context.getUserId(), invite.getTeamId())) {
            throw new InviteConflictException("Your account is already a member of this team.");
        }

        Team team = teamRepository.findById(invite.getTeamId())
                .orElseThrow(() -> new InviteNotFoundException());
        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(context.getUserId());
        member.setRole(invite.getRole());
        teamMemberRepository.save(member);

        invite.setStatus(TeamConstant.ACCEPTED.name());
        inviteRepository.save(invite);
        return new InviteAcceptanceResponse(team.getId(), team.getName(), invite.getRole());
    }

    private Invite findInvite(UUID token) {
        if (token == null) {
            throw new InviteNotFoundException();
        }
        return inviteRepository.findByToken(token).orElseThrow(InviteNotFoundException::new);
    }

    private void assertInviteIsPending(Invite invite) {
        if (!TeamConstant.INVITED.name().equals(invite.getStatus())) {
            throw new InviteConflictException("This invitation has already been used or is no longer active.");
        }
        if (invite.getCreatedAt() == null || invite.getTtl() == null
                || Instant.now().isAfter(invite.getCreatedAt().plus(invite.getTtl()))) {
            throw new InviteExpiredException();
        }
    }

    private String normalizeAndValidateEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank() || rawEmail.length() > 254) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        try {
            InternetAddress address = new InternetAddress(email, true);
            address.validate();
            return email;
        } catch (AddressException e) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
    }

    private Role parseInvitedRole(String rawRole) {
        if (rawRole == null) {
            throw new IllegalArgumentException("Choose a role for the invited member.");
        }
        Role role;
        try {
            role = Role.valueOf(rawRole.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Choose either ADMIN or USER for the invited member.");
        }
        if (role != Role.ADMIN && role != Role.USER) {
            throw new IllegalArgumentException("Invitations can only be created for ADMIN or USER roles.");
        }
        return role;
    }

    private Role parseInviterRole(String rawRole) {
        try {
            return Role.valueOf(rawRole);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AccessDeniedException("You are not authorized to invite team members.");
        }
    }
}
