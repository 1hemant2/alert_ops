package com.alertops.flow.application;

import com.alertops.auth.model.User;
import com.alertops.auth.repository.UserRepository;
import com.alertops.flow.dto.CreateNodeDto;
import com.alertops.flow.model.Node;
import com.alertops.flow.repository.FlowRepository;
import com.alertops.flow.repository.NodeRepository;
import com.alertops.flow.service.FlowService;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import com.alertops.team.model.TeamMember;
import com.alertops.team.repository.TeamMemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreateFlowNodeUseCaseTest {
    private final FlowRepository flowRepository = mock(FlowRepository.class);
    private final NodeRepository nodeRepository = mock(NodeRepository.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CreateFlowNodeUseCase useCase = new CreateFlowNodeUseCase(
            flowRepository, nodeRepository, teamMemberRepository, userRepository);

    @AfterEach
    void clearContext() {
        AuthContextHolder.clear();
    }

    @Test
    void nodeCannotBeAddedToFlowOutsideSelectedTeam() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(UUID.randomUUID(), teamId, "TEAM_OWNER", "token", "owner@example.com"));

        User recipient = mock(User.class);
        when(recipient.getId()).thenReturn(userId);
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(recipient);
        TeamMember membership = new TeamMember();
        membership.setTeamId(teamId);
        when(teamMemberRepository.findTeamMemeber(userId, teamId)).thenReturn(membership);
        when(flowRepository.findByIdAndTeamId(flowId, teamId)).thenReturn(null);
        CreateNodeDto request = new CreateNodeDto();
        request.setFlowId(flowId);
        request.setEmail("recipient@example.com");

        assertThrows(RuntimeException.class, () -> useCase.execute(request, mock(FlowService.class)));

        verify(flowRepository).findByIdAndTeamId(flowId, teamId);
        verify(nodeRepository, never()).save(any(Node.class));
    }
}
