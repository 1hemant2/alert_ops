package com.alertops.flow.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import com.alertops.auth.model.User;
import com.alertops.auth.repository.UserRepository;
import com.alertops.flow.model.*;
import com.alertops.flow.repository.*;
import com.alertops.flow.dto.CreateNodeDto;
import com.alertops.flow.exception.FlowException;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;
import com.alertops.team.model.TeamMember;
import com.alertops.team.repository.TeamMemberRepository;

import jakarta.transaction.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;

@Service
//@Slf4j
public class FlowService {

      private final FlowRepository flowRepository;
      private final NodeRepository nodeRepository;
      private final TeamMemberRepository teamMemberRepository;
      private final UserRepository userRepository;

    FlowService (FlowRepository flowRepository, NodeRepository nodeRepository,
                 TeamMemberRepository teamMemberRepository, UserRepository userRepository) {
       this.flowRepository = flowRepository;
       this.nodeRepository = nodeRepository;
       this.teamMemberRepository = teamMemberRepository;
       this.userRepository = userRepository;
   }

    public Flow createFlow(String flowName) {
        try {
            AuthContext authContext = AuthContextHolder.get();
            Flow flow = new Flow();
            flow.setName(flowName);
            flow.setTeamId(authContext.getTeamId());
            flow.setUpdatedBy(authContext.getUserId());
            Flow savedFlow = flowRepository.save(flow);
            return savedFlow;
        } catch (Exception e) {
            throw e;
        }
    }

    public Flow getFlowById(UUID flowId) {
        try {
            AuthContext authContext = AuthContextHolder.get();
            if(flowId == null) return null; 
            Optional<Flow> flowOpt = flowRepository.findById(flowId);
            if(flowOpt.isEmpty()) {
                return null;
            }
            Flow flow = flowOpt.get();
            if(!flow.getTeamId().equals(authContext.getTeamId())) {
                return null;
            }
            return flow;
        } catch(Exception e) {
            throw e;
        }
    }

    public List<Flow> getFlowsByTeamId(int page, int size, String sortBy, String sortDir) {
        try {
            AuthContext authContext = AuthContextHolder.get();
            UUID teamId = authContext.getTeamId();
            Set<String> allowedSortBy = Set.of("flowName", "createdAt");
            Set<String> allowedSortDir = Set.of("asc", "desc");

            if(page < 0) {
                page = 0;
            }

            if(!allowedSortBy.contains(sortBy)) {
                sortBy = "createdAt";
            }

            if( !allowedSortDir.contains(sortDir)) {
                sortDir = "asc";
            }

            Sort sort = Sort.by(Sort.Direction.valueOf(sortDir.toUpperCase()), sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<Flow> flowPage = flowRepository.findByTeamId(teamId, pageable);
            List<Flow> flows = flowPage.getContent();
            return flows;
        } catch(Exception e) {
            throw e;
        }
    }

    public Node createNode(UUID flowId, String nodeName, int durationInMinutes, UUID createdBy, BigInteger position, String email) {
        try {

            if(flowId == null) {
                throw new RuntimeException("Flow ID is required");
            }   

            Node node = new Node();
            node.setName(nodeName);
            node.setFlowId(flowId);
            node.setCreatedBy(createdBy);
            node.setDuration(java.time.Duration.ofMinutes(durationInMinutes));
            node.setEmail(email);

            if(position.compareTo(BigInteger.ZERO) > 0) {
                node.setPosition(position .add(BigInteger.valueOf(1000)));
            } else {
                node.setPosition(BigInteger.valueOf(1000));
            }
            
            return node;
        } catch (Exception e) {
            throw e;
        }
    }

    public List<Node> getNodesByFlowId(UUID flowId) {
        try {

            if(flowId == null) {
                throw new RuntimeException("Flow ID is required");
            }   

            AuthContext authContext = AuthContextHolder.get();
            Flow flow = flowRepository.findByIdAndTeamId(flowId, authContext.getTeamId());

            if(flow == null) {
                throw new RuntimeException("Flow not found");
            }

            List<Node> nodes = nodeRepository.findAllByFlowIdOrderByPositionAsc(flow.getId());

            return nodes;
        } catch(Exception e) {
            throw e;
        }
    }

    @Transactional
    public Node reorderNode(UUID nodeId, UUID afterNodeId, Long version) {
        try {

            if(nodeId == null) {
                throw new RuntimeException("Node ID is required");
            }

            Node node = nodeRepository.findById(nodeId).orElse(null);
            
            Node afterNode = null;

            if(afterNodeId != null) {
                afterNode = nodeRepository.findById(afterNodeId).orElse(null);
            }

            Flow flow = flowRepository.findById(node.getFlowId()).orElse(null);

            if(flow == null) {
                throw new RuntimeException("Flow not found");
            }

            AuthContext authContext = AuthContextHolder.get();
            if (!flow.getTeamId().equals(authContext.getTeamId())) {
                throw new RuntimeException("Flow not found");
            }

            if (version == null || !flow.getVersion().equals(version)) {
                throw new RuntimeException("Flow has been updated by other user. Please refresh and try again.");
            }

            if (afterNode != null && (!afterNode.getFlowId().equals(node.getFlowId())
                    || afterNode.getId().equals(node.getId()))) {
                throw new RuntimeException("Choose a different node in the same flow");
            }

            List<Node> nodes = new ArrayList<>(nodeRepository.findAllByFlowIdOrderByPositionAsc(node.getFlowId()));
            nodes.removeIf(existing -> existing.getId().equals(nodeId));
            int insertAt = afterNode == null ? 0 : -1;
            if (afterNode != null) {
                for (int index = 0; index < nodes.size(); index++) {
                    if (nodes.get(index).getId().equals(afterNodeId)) {
                        insertAt = index + 1;
                        break;
                    }
                }
                if (insertAt < 0) {
                    throw new RuntimeException("Target node not found in this flow");
                }
            }
            nodes.add(insertAt, node);

            BigInteger position = BigInteger.valueOf(1000);
            for (Node current : nodes) {
                current.setPosition(position);
                position = position.add(BigInteger.valueOf(1000));
            }
            nodeRepository.saveAll(nodes);
            flow.setUpdatedBy(authContext.getUserId());
            flow.setUpdatedAt(Instant.now());
            flowRepository.saveAndFlush(flow);
            return node;
        } catch(Exception e) {
            throw e;
        }
    }

    @Transactional
    public CreateNodeDto updateNode(UUID nodeId, String nodeName, int durationInMinutes, String email, Long version) {
        AuthContext authContext = requireAuthContext();
        Node node = nodeRepository.findById(nodeId).orElse(null);
        if (node == null) throw FlowException.stepNotFound();
        Flow flow = flowRepository.findByIdAndTeamId(node.getFlowId(), authContext.getTeamId());
        if (flow == null) throw FlowException.stepNotFound();
        requireCurrentVersion(flow, version);
        validateStep(nodeName, durationInMinutes, email);

        User user = userRepository.findByEmailIgnoreCase(email.trim());
        if (user == null) throw FlowException.invalid("Choose a member of this team as the contact.");
        TeamMember member = teamMemberRepository.findTeamMemeber(user.getId(), authContext.getTeamId());
        if (member == null) throw FlowException.invalid("Choose a member of this team as the contact.");

        node.setName(nodeName.trim());
        node.setDuration(java.time.Duration.ofMinutes(durationInMinutes));
        node.setEmail(user.getEmail());
        nodeRepository.save(node);
        touchFlow(flow, authContext);
        flowRepository.saveAndFlush(flow);
        return toNodeDto(node);
    }

    @Transactional
    public void deleteNode(UUID nodeId, Long version) {
        AuthContext authContext = requireAuthContext();
        if (nodeId == null) throw FlowException.stepNotFound();
        Node node = nodeRepository.findById(nodeId).orElse(null);
        if (node == null) throw FlowException.stepNotFound();
        Flow flow = flowRepository.findByIdAndTeamId(node.getFlowId(), authContext.getTeamId());
        if (flow == null) throw FlowException.stepNotFound();
        requireCurrentVersion(flow, version);

        List<Node> remainingNodes = new ArrayList<>(nodeRepository.findAllByFlowIdOrderByPositionAsc(flow.getId()));
        remainingNodes.removeIf(existing -> existing.getId().equals(nodeId));
        BigInteger position = BigInteger.valueOf(1000);
        for (Node remaining : remainingNodes) {
            remaining.setPosition(position);
            position = position.add(BigInteger.valueOf(1000));
        }

        nodeRepository.delete(node);
        nodeRepository.saveAll(remainingNodes);
        touchFlow(flow, authContext);
        flowRepository.saveAndFlush(flow);
    }

    private AuthContext requireAuthContext() {
        AuthContext authContext = AuthContextHolder.get();
        if (authContext == null || authContext.getTeamId() == null) {
            throw FlowException.stepNotFound();
        }
        return authContext;
    }

    private void requireCurrentVersion(Flow flow, Long version) {
        if (version == null || !flow.getVersion().equals(version)) throw FlowException.staleVersion();
    }

    private void validateStep(String nodeName, int durationInMinutes, String email) {
        if (nodeName == null || nodeName.isBlank() || nodeName.trim().length() > 100) {
            throw FlowException.invalid("Step name is required and must be 100 characters or fewer.");
        }
        if (durationInMinutes < 0 || durationInMinutes > 10080) {
            throw FlowException.invalid("Wait time must be between 0 and 10,080 minutes.");
        }
        if (email == null || email.isBlank()) throw FlowException.invalid("Choose a team member for this step.");
    }

    private void touchFlow(Flow flow, AuthContext authContext) {
        flow.setUpdatedBy(authContext.getUserId());
        flow.setUpdatedAt(Instant.now());
    }

    private CreateNodeDto toNodeDto(Node node) {
        return new CreateNodeDto(node.getId(), node.getFlowId(), node.getName(),
                Math.toIntExact(node.getDuration().toMinutes()), node.getEmail(), node.getPosition());
    }

}
