package com.alertops.flow.application;

import java.math.BigInteger;

import org.springframework.stereotype.Service;

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
import  com.alertops.flow.model.Flow;

@Service
public class CreateFlowNodeUseCase {

    private final FlowRepository flowRepository;
    private final NodeRepository nodeRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    // private final FlowService flowService;

    public CreateFlowNodeUseCase(FlowRepository flowRepository, 
                                    NodeRepository nodeRepository,
                                    TeamMemberRepository teamMemberRepository,
                                    UserRepository userRepository
                                ) {
        this.flowRepository = flowRepository;
        this.nodeRepository = nodeRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
    }

    public CreateNodeDto execute(CreateNodeDto request, FlowService flowService) {

        try {
            AuthContext authContext = AuthContextHolder.get();
      
            //get user email whom node is being created for.
            User user = userRepository.findByEmail(request.getEmail()); 
            if(user == null) {
                throw new RuntimeException("User not found");
            }

            TeamMember teamMember = teamMemberRepository.findTeamMemeber(user.getId(), authContext.getTeamId());

            if(teamMember == null) {
                throw new RuntimeException("User is not a member of the team");
            }

            Flow flow = flowRepository.findById(request.getFlowId()).orElse(null);

            if(flow == null) {
                throw new RuntimeException("Flow not found");
            }


            // 2. Cross-domain validation (APPLICATION responsibility)
            if (!teamMember.getTeamId().equals(authContext.getTeamId())) {
                throw new RuntimeException("User not in same team");
            }

            Node lastNode = nodeRepository.findTopByFlowIdOrderByPositionDesc(flow.getId());
            
            // 3. Call domain behavior
            Node node = flowService.createNode(
                flow.getId(),
                request.getNodeName(),
                request.getDurationInMinutes(),
                authContext.getUserId(),
                lastNode == null ? BigInteger.valueOf(0) : lastNode.getPosition(),
                request.getEmail()
            );

            if(node == null) {
                throw new RuntimeException("Error creating node");
            }   
            // 4. Persist
            nodeRepository.save(node);
            flowRepository.save(flow);

            // 5. Map to response DTO
            return new CreateNodeDto(
                node.getId(),
                node.getFlowId(),
                node.getName(),
                (int)node.getDuration().toMinutes(),
                user.getEmail(),
                node.getPosition()
            );
        } catch (Exception e) {
            throw new RuntimeException("Error creating flow node: " + e.getMessage());
        }


    }

}
