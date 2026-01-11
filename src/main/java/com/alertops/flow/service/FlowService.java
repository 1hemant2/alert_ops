package com.alertops.flow.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import com.alertops.flow.model.*;
import com.alertops.flow.repository.*;
import com.alertops.security.AuthContext;
import com.alertops.security.AuthContextHolder;

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

    FlowService (FlowRepository flowRepository, NodeRepository nodeRepository) {
       this.flowRepository = flowRepository;
       this.nodeRepository = nodeRepository;
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

    public Node createNode(UUID flowId, String nodeName, int durationInMinutes, UUID createdBy, BigInteger position) {
        try {

            if(flowId == null) {
                throw new RuntimeException("Flow ID is required");
            }   

            Node node = new Node();
            node.setName(nodeName);
            node.setFlowId(flowId);
            node.setCreatedBy(createdBy);
            node.setDuration(java.time.Duration.ofMinutes(durationInMinutes));

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

            Flow flow = flowRepository.findById(flowId).orElse(null);

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

            if(!flow.getVersion().equals(version)) {
                throw new RuntimeException("Flow has been updated by other user. Please refresh and try again.");
            }

            BigInteger newPosition;

            /**
             * Calculate new position based on afterNode position
             * If afterNode is null, move to the start
             * Else, find the next node after afterNode position and set position in between
             * If next node is null, set position after afterNode position
             */
            if(afterNode == null) {
                Node firstNode = nodeRepository.findTopByFlowIdOrderByPositionAsc(node.getFlowId());
                if(firstNode == null) {
                    throw new RuntimeException("No nodes found in the flow");
                } else {
                    newPosition = firstNode.getPosition().subtract(BigInteger.valueOf(1000));
                }
            } else {
                
                Node nextNode = nodeRepository.findNextNode(node.getFlowId(), afterNode.getPosition());

                if(nextNode == null) {
                    newPosition = afterNode.getPosition().add(BigInteger.valueOf(1000));
                    node.setPosition(newPosition);
                } else {
                    newPosition = afterNode.getPosition().add(nextNode.getPosition()).divide(BigInteger.valueOf(2));
                    node.setPosition(newPosition);
                }  
            }
            
            System.out.println("New Position: " + newPosition);

            if(newPosition.compareTo(BigInteger.valueOf(50)) < 50) {
               // reindex
            }

            node.setPosition(newPosition);
            Node updatedNode = nodeRepository.save(node);
            flow.setUpdatedBy(flow.getId());
            flow.setUpdatedAt(Instant.now());
            flowRepository.updateFlow(flow);
            return updatedNode;
        } catch(Exception e) {
            throw e;
        }
    }

}

