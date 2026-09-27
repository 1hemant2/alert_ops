package com.alertops.team.service;

import com.alertops.permissions.GrantPermission;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import com.alertops.security.JwtUtil;
import com.alertops.team.dto.TeamListItemDto;
import com.alertops.team.dto.TeamMemberResponseDto;
import com.alertops.team.repository.TeamListItemProjection;
import com.alertops.team.repository.TeamMemberRepository;
import com.alertops.team.repository.TeamMemberProjection;
import com.alertops.team.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamServiceTest {
    private static final UUID USER_ID = UUID.fromString("42000000-0000-0000-0000-000000000001");
    private static final UUID TEAM_ID = UUID.fromString("42000000-0000-0000-0000-000000000002");

    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final TeamService teamService = new TeamService(
            mock(GrantPermission.class),
            mock(TeamRepository.class),
            mock(JwtUtil.class),
            teamMemberRepository);

    @AfterEach
    void clearAuthContext() {
        AuthContextHolder.clear();
    }

    @Test
    void returnsMembersOnlyForTheSelectedTeam() {
        AuthContextHolder.set(new AuthContext(USER_ID, TEAM_ID, "USER", "token", "person@example.com"));
        List<TeamMemberResponseDto> expected = List.of(
                new TeamMemberResponseDto(
                        UUID.fromString("42000000-0000-0000-0000-000000000003"),
                        USER_ID,
                        "Alex Example",
                        "person@example.com",
                        "USER"));
        TeamMemberProjection projection = mock(TeamMemberProjection.class);
        when(projection.getMemberId()).thenReturn(expected.get(0).memberId());
        when(projection.getUserId()).thenReturn(expected.get(0).userId());
        when(projection.getName()).thenReturn(expected.get(0).name());
        when(projection.getEmail()).thenReturn(expected.get(0).email());
        when(projection.getRole()).thenReturn(expected.get(0).role());
        when(teamMemberRepository.findMembersByTeamId(TEAM_ID)).thenReturn(List.of(projection));

        assertThat(teamService.getTeamMembers()).containsExactlyElementsOf(expected);
        verify(teamMemberRepository).findMembersByTeamId(TEAM_ID);
    }

    @Test
    void includesTheCurrentRoleInEachTeamListRow() {
        AuthContextHolder.set(new AuthContext(USER_ID, null, null, "token", "person@example.com"));
        TeamListItemProjection projection = mock(TeamListItemProjection.class);
        when(projection.getId()).thenReturn(TEAM_ID);
        when(projection.getName()).thenReturn("Example Team");
        when(projection.getRole()).thenReturn("TEAM_OWNER");
        when(teamMemberRepository.findTeamListByUserId(USER_ID)).thenReturn(List.of(projection));

        assertThat(teamService.getUserTeams()).containsExactly(new TeamListItemDto(TEAM_ID, "Example Team", "TEAM_OWNER"));
    }

    @Test
    void refusesToReturnMembersWithoutASelectedTeam() {
        AuthContextHolder.set(new AuthContext(USER_ID, null, null, "token", "person@example.com"));

        assertThrows(AccessDeniedException.class, teamService::getTeamMembers);
    }
}
