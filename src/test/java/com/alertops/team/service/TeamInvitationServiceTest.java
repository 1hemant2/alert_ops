package com.alertops.team.service;

import com.alertops.auth.repository.UserRepository;
import com.alertops.messaging.TeamInvitationMailer;
import com.alertops.permissions.GrantPermission;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import com.alertops.team.constant.TeamConstant;
import com.alertops.team.dto.InviteAcceptanceResponse;
import com.alertops.team.dto.InviteDtoReq;
import com.alertops.team.dto.InviteResponseDto;
import com.alertops.team.model.Invite;
import com.alertops.team.model.Team;
import com.alertops.team.model.TeamMember;
import com.alertops.team.repository.InviteRepository;
import com.alertops.team.repository.TeamMemberRepository;
import com.alertops.team.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TeamInvitationServiceTest {
    private static final UUID OWNER_ID = UUID.fromString("43000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_ID = UUID.fromString("43000000-0000-0000-0000-000000000002");
    private static final UUID TEAM_ID = UUID.fromString("43000000-0000-0000-0000-000000000003");
    private static final UUID INVITE_TOKEN = UUID.fromString("43000000-0000-0000-0000-000000000004");

    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final InviteRepository inviteRepository = mock(InviteRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TeamInvitationMailer mailer = mock(TeamInvitationMailer.class);
    private final TeamInvitationService service = new TeamInvitationService(
            new GrantPermission(), teamRepository, teamMemberRepository, inviteRepository,
            userRepository, mailer, "https://alerts.example.com");

    @AfterEach
    void clearAuthContext() {
        AuthContextHolder.clear();
    }

    @Test
    void sendsAnInvitationAndReturnsNoRawToken() {
        AuthContextHolder.set(new AuthContext(OWNER_ID, TEAM_ID, "TEAM_OWNER", "jwt", "owner@example.com"));
        Team team = team();
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team));
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(null);
        when(inviteRepository.existsByTeamIdAndEmailIgnoreCaseAndStatus(
                TEAM_ID, "user@example.com", TeamConstant.INVITED.name())).thenReturn(false);
        when(inviteRepository.saveAndFlush(any(Invite.class))).thenAnswer(invocation -> {
            Invite invite = invocation.getArgument(0);
            invite.setCreatedAt(Instant.now());
            return invite;
        });

        InviteResponseDto response = service.createInvite(new InviteDtoReq(
                "User@Example.com", "USER", 72L));

        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.role()).isEqualTo("USER");
        assertThat(response.teamName()).isEqualTo("Example Team");
        assertThat(response.expiresAt()).isAfter(Instant.now());
        ArgumentCaptor<Invite> inviteCaptor = ArgumentCaptor.forClass(Invite.class);
        verify(inviteRepository).saveAndFlush(inviteCaptor.capture());
        Invite createdInvite = inviteCaptor.getValue();
        assertThat(createdInvite.getToken()).isNotNull();
        verify(mailer).sendInvitation(eq(createdInvite), eq(team), eq(
                "https://alerts.example.com/join?token=" + createdInvite.getToken()));
    }

    @Test
    void deniesMembersAndAdminsCannotInviteAnotherAdmin() {
        AuthContextHolder.set(new AuthContext(MEMBER_ID, TEAM_ID, "USER", "jwt", "member@example.com"));
        assertThrows(AccessDeniedException.class,
                () -> service.createInvite(new InviteDtoReq("person@example.com", "USER", 24L)));
        verifyNoInteractions(teamRepository, teamMemberRepository, inviteRepository, userRepository, mailer);

        AuthContextHolder.set(new AuthContext(MEMBER_ID, TEAM_ID, "ADMIN", "jwt", "admin@example.com"));
        assertThrows(AccessDeniedException.class,
                () -> service.createInvite(new InviteDtoReq("person@example.com", "ADMIN", 24L)));
        verifyNoInteractions(teamRepository, teamMemberRepository, inviteRepository, userRepository, mailer);
    }

    @Test
    void rejectsInvalidEmailsAndExpiryWindows() {
        AuthContextHolder.set(new AuthContext(OWNER_ID, TEAM_ID, "TEAM_OWNER", "jwt", "owner@example.com"));

        assertThrows(IllegalArgumentException.class,
                () -> service.createInvite(new InviteDtoReq("not-an-email", "USER", 24L)));
        assertThrows(IllegalArgumentException.class,
                () -> service.createInvite(new InviteDtoReq("person@example.com", "USER", 0L)));
        verifyNoInteractions(teamRepository, teamMemberRepository, inviteRepository, userRepository, mailer);
    }

    @Test
    void acceptsInvitationAndAddsMembershipAtomically() {
        AuthContextHolder.set(new AuthContext(MEMBER_ID, null, null, "jwt", "user@example.com"));
        Invite invite = pendingInvite("user@example.com");
        Team team = team();
        when(inviteRepository.findByTokenForUpdate(INVITE_TOKEN)).thenReturn(Optional.of(invite));
        when(teamMemberRepository.existsByUserIdAndTeamId(MEMBER_ID, TEAM_ID)).thenReturn(false);
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team));

        InviteAcceptanceResponse result = service.accept(INVITE_TOKEN);

        assertThat(result.teamId()).isEqualTo(TEAM_ID);
        assertThat(result.teamName()).isEqualTo("Example Team");
        assertThat(result.role()).isEqualTo("USER");
        assertThat(invite.getStatus()).isEqualTo(TeamConstant.ACCEPTED.name());
        verify(teamMemberRepository).save(any(TeamMember.class));
        verify(inviteRepository).save(invite);
    }

    @Test
    void rejectsInviteAcceptanceFromAnotherEmail() {
        AuthContextHolder.set(new AuthContext(MEMBER_ID, null, null, "jwt", "someone-else@example.com"));
        Invite invite = pendingInvite("user@example.com");
        when(inviteRepository.findByTokenForUpdate(INVITE_TOKEN)).thenReturn(Optional.of(invite));

        assertThrows(AccessDeniedException.class, () -> service.accept(INVITE_TOKEN));
        verifyNoInteractions(teamRepository, teamMemberRepository);
    }

    @Test
    void previewRejectsExpiredInvitations() {
        Invite invite = pendingInvite("user@example.com");
        invite.setCreatedAt(Instant.now().minus(Duration.ofDays(4)));
        when(inviteRepository.findByToken(INVITE_TOKEN)).thenReturn(Optional.of(invite));

        assertThrows(InviteExpiredException.class, () -> service.preview(INVITE_TOKEN));
        verify(teamRepository, never()).findById(any());
    }

    private Team team() {
        Team team = mock(Team.class);
        when(team.getId()).thenReturn(TEAM_ID);
        when(team.getName()).thenReturn("Example Team");
        return team;
    }

    private Invite pendingInvite(String email) {
        Invite invite = new Invite();
        invite.setToken(INVITE_TOKEN);
        invite.setEmail(email);
        invite.setRole("USER");
        invite.setTeamId(TEAM_ID);
        invite.setTtl(Duration.ofHours(72));
        invite.setCreatedAt(Instant.now());
        invite.setStatus(TeamConstant.INVITED.name());
        return invite;
    }
}
