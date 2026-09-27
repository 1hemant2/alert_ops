package com.alertops.flow.service;

import com.alertops.auth.model.User;
import com.alertops.auth.repository.UserRepository;
import com.alertops.team.model.TeamMember;
import com.alertops.team.repository.TeamMemberRepository;
import com.alertops.flow.model.Flow;
import com.alertops.flow.model.Node;
import com.alertops.flow.repository.FlowRepository;
import com.alertops.flow.repository.NodeRepository;
import com.alertops.flow.exception.FlowException;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FlowServiceTest {
    private final FlowRepository flowRepository = mock(FlowRepository.class);
    private final NodeRepository nodeRepository = mock(NodeRepository.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FlowService flowService = new FlowService(flowRepository, nodeRepository, teamMemberRepository, userRepository);

    @AfterEach
    void clearContext() {
        AuthContextHolder.clear();
    }

    @Test
    void reorderMovesNodeAfterTargetAndReindexesPositions() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID movingId = UUID.randomUUID();
        UUID afterId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(userId, teamId, "TEAM_OWNER", "token", "owner@example.com"));

        Flow flow = mock(Flow.class);
        when(flow.getId()).thenReturn(flowId);
        when(flow.getTeamId()).thenReturn(teamId);
        when(flow.getVersion()).thenReturn(4L);
        Node first = node(firstId, flowId);
        Node moving = node(movingId, flowId);
        Node after = node(afterId, flowId);
        when(nodeRepository.findById(movingId)).thenReturn(Optional.of(moving));
        when(nodeRepository.findById(afterId)).thenReturn(Optional.of(after));
        when(flowRepository.findById(flowId)).thenReturn(Optional.of(flow));
        when(nodeRepository.findAllByFlowIdOrderByPositionAsc(flowId)).thenReturn(List.of(first, moving, after));

        flowService.reorderNode(movingId, afterId, 4L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Node>> savedNodes = ArgumentCaptor.forClass(Iterable.class);
        verify(nodeRepository).saveAll(savedNodes.capture());
        List<Node> ordered = StreamSupport.stream(savedNodes.getValue().spliterator(), false).toList();
        assertEquals(List.of(firstId, afterId, movingId), ordered.stream().map(Node::getId).toList());
        verify(first).setPosition(BigInteger.valueOf(1000));
        verify(after).setPosition(BigInteger.valueOf(2000));
        verify(moving).setPosition(BigInteger.valueOf(3000));
        verify(flowRepository).saveAndFlush(flow);
    }

    @Test
    void reorderRejectsStaleVersionWithoutWritingNodes() {
        UUID teamId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(UUID.randomUUID(), teamId, "TEAM_OWNER", "token", "owner@example.com"));
        Flow flow = mock(Flow.class);
        when(flow.getId()).thenReturn(flowId);
        when(flow.getTeamId()).thenReturn(teamId);
        when(flow.getVersion()).thenReturn(5L);
        Node node = node(nodeId, flowId);
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(flowRepository.findById(flowId)).thenReturn(Optional.of(flow));

        assertThrows(RuntimeException.class, () -> flowService.reorderNode(nodeId, null, 4L));

        verify(nodeRepository, never()).saveAll(any());
    }

    @Test
    void nodeReadIsScopedToSelectedTeam() {
        UUID teamId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(UUID.randomUUID(), teamId, "TEAM_OWNER", "token", "owner@example.com"));
        when(flowRepository.findByIdAndTeamId(flowId, teamId)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> flowService.getNodesByFlowId(flowId));

        verify(nodeRepository, never()).findAllByFlowIdOrderByPositionAsc(any());
    }

    @Test
    void updateNodeChangesContactAndIncrementsTheFlowVersion() {
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(userId, teamId, "TEAM_OWNER", "token", "owner@example.com"));
        Flow flow = mock(Flow.class);
        when(flow.getId()).thenReturn(flowId);
        when(flow.getVersion()).thenReturn(3L);
        Node node = mock(Node.class);
        when(node.getId()).thenReturn(nodeId);
        when(node.getFlowId()).thenReturn(flowId);
        when(node.getName()).thenReturn("edited escalation step");
        when(node.getDuration()).thenReturn(Duration.ofMinutes(12));
        when(node.getEmail()).thenReturn("member@example.com");
        when(node.getPosition()).thenReturn(BigInteger.valueOf(2000));
        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getEmail()).thenReturn("member@example.com");
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(flowRepository.findByIdAndTeamId(flowId, teamId)).thenReturn(flow);
        when(userRepository.findByEmailIgnoreCase("member@example.com")).thenReturn(user);
        when(teamMemberRepository.findTeamMemeber(user.getId(), teamId)).thenReturn(mock(TeamMember.class));

        var updated = flowService.updateNode(nodeId, " edited escalation step ", 12, "member@example.com", 3L);

        assertEquals("edited escalation step", updated.getNodeName());
        assertEquals(12, updated.getDurationInMinutes());
        verify(node).setName("edited escalation step");
        verify(node).setDuration(Duration.ofMinutes(12));
        verify(node).setEmail("member@example.com");
        verify(nodeRepository).save(node);
        verify(flowRepository).saveAndFlush(flow);
    }

    @Test
    void updateNodeRejectsAStaleFlowVersion() {
        UUID teamId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(UUID.randomUUID(), teamId, "TEAM_OWNER", "token", "owner@example.com"));
        Flow flow = mock(Flow.class);
        when(flow.getVersion()).thenReturn(5L);
        Node node = node(nodeId, flowId);
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(flowRepository.findByIdAndTeamId(flowId, teamId)).thenReturn(flow);

        assertThrows(FlowException.class, () -> flowService.updateNode(nodeId, "step", 5, "member@example.com", 4L));

        verify(nodeRepository, never()).save(any());
        verify(flowRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateNodeDoesNotExposeStepsFromAnotherTeam() {
        UUID teamId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(UUID.randomUUID(), teamId, "TEAM_OWNER", "token", "owner@example.com"));
        Node node = node(nodeId, flowId);
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(flowRepository.findByIdAndTeamId(flowId, teamId)).thenReturn(null);

        assertThrows(FlowException.class, () -> flowService.updateNode(nodeId, "step", 5, "member@example.com", 4L));

        verify(nodeRepository, never()).save(any());
    }

    @Test
    void updateNodeRequiresTheContactToBeOnTheSelectedTeam() {
        UUID teamId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(UUID.randomUUID(), teamId, "TEAM_OWNER", "token", "owner@example.com"));
        Flow flow = mock(Flow.class);
        when(flow.getVersion()).thenReturn(2L);
        Node node = node(nodeId, flowId);
        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(flowRepository.findByIdAndTeamId(flowId, teamId)).thenReturn(flow);
        when(userRepository.findByEmailIgnoreCase("member@example.com")).thenReturn(user);
        when(teamMemberRepository.findTeamMemeber(user.getId(), teamId)).thenReturn(null);

        assertThrows(FlowException.class, () -> flowService.updateNode(nodeId, "step", 5, "member@example.com", 2L));

        verify(nodeRepository, never()).save(any());
        verify(flowRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteNodeRemovesStepAndReindexesRemainingSteps() {
        UUID teamId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        UUID remainingId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(UUID.randomUUID(), teamId, "TEAM_OWNER", "token", "owner@example.com"));
        Flow flow = mock(Flow.class);
        when(flow.getId()).thenReturn(flowId);
        when(flow.getVersion()).thenReturn(2L);
        Node deleted = node(deletedId, flowId);
        Node remaining = node(remainingId, flowId);
        when(nodeRepository.findById(deletedId)).thenReturn(Optional.of(deleted));
        when(flowRepository.findByIdAndTeamId(flowId, teamId)).thenReturn(flow);
        when(nodeRepository.findAllByFlowIdOrderByPositionAsc(flowId)).thenReturn(List.of(deleted, remaining));

        flowService.deleteNode(deletedId, 2L);

        verify(nodeRepository).delete(deleted);
        verify(remaining).setPosition(BigInteger.valueOf(1000));
        verify(nodeRepository).saveAll(List.of(remaining));
        verify(flowRepository).saveAndFlush(flow);
    }

    @Test
    void deleteNodeRejectsAStaleFlowVersion() {
        UUID teamId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(UUID.randomUUID(), teamId, "TEAM_OWNER", "token", "owner@example.com"));
        Flow flow = mock(Flow.class);
        when(flow.getVersion()).thenReturn(6L);
        Node node = node(nodeId, flowId);
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(flowRepository.findByIdAndTeamId(flowId, teamId)).thenReturn(flow);

        assertThrows(FlowException.class, () -> flowService.deleteNode(nodeId, 5L));

        verify(nodeRepository, never()).delete(any());
    }

    @Test
    void deleteNodeDoesNotExposeStepsFromAnotherTeam() {
        UUID teamId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        AuthContextHolder.set(new AuthContext(UUID.randomUUID(), teamId, "TEAM_OWNER", "token", "owner@example.com"));
        Node node = node(nodeId, flowId);
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(flowRepository.findByIdAndTeamId(flowId, teamId)).thenReturn(null);

        assertThrows(FlowException.class, () -> flowService.deleteNode(nodeId, 2L));

        verify(nodeRepository, never()).delete(any());
    }

    private Node node(UUID id, UUID flowId) {
        Node node = mock(Node.class);
        when(node.getId()).thenReturn(id);
        when(node.getFlowId()).thenReturn(flowId);
        return node;
    }
}
