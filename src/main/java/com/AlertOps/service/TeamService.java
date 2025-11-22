package com.AlertOps.service;

import com.AlertOps.model.Team;
import com.AlertOps.model.User;
import com.AlertOps.repository.TeamRepository;
import com.AlertOps.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TeamService {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.AlertOps.repository.InvitationRepository invitationRepository;

    @Autowired
    private EmailService emailService;

    public Team createTeam(Team team) {
        return teamRepository.save(team);
    }

    public Team getTeam(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Team not found with id: " + id));
    }

    public void addUserToTeam(Long teamId, String email) {
        Team team = getTeam(teamId);
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("User not found with email: " + email);
        }
        
        user.getTeams().add(team);
        userRepository.save(user);
    }

    public void createDefaultTeamForUser(User user) {
        Team team = new Team();
        team.setName(user.getName() + "'s Team");
        team.setDescription("Default team for " + user.getName());
        team = teamRepository.save(team);
        
        user.getTeams().add(team);
        userRepository.save(user);
    }

    public void createInvitation(Long teamId, String email) {
        // Check if user is already in team? Maybe later.
        
        String token = java.util.UUID.randomUUID().toString();
        com.AlertOps.model.Invitation invitation = new com.AlertOps.model.Invitation();
        invitation.setEmail(email);
        invitation.setTeamId(teamId);
        invitation.setToken(token);
        invitation.setExpiryDate(new java.util.Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000)); // 24 hours
        
        invitationRepository.save(invitation);
        
        String inviteLink = "http://localhost:8080/api/teams/join?token=" + token; // Or frontend URL
        emailService.sendSimpleMessage(email, "Team Invitation", "You have been invited to join a team. Click here: " + inviteLink);
    }

    public void joinTeam(String token, String userEmail) {
        com.AlertOps.model.Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid invitation token"));
        
        if (invitation.getExpiryDate().before(new java.util.Date())) {
            throw new RuntimeException("Invitation expired");
        }
        
        // Verify email matches if needed, or just allow whoever has token?
        // User request says: "on join user will join the person team who have invited that user"
        // Typically we check if the logged in user matches the invite email, but for simplicity let's assume token is enough or we pass email.
        
        addUserToTeam(invitation.getTeamId(), userEmail);
        
        // Delete invitation after use
        invitationRepository.delete(invitation);
    }
}
