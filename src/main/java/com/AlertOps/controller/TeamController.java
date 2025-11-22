package com.AlertOps.controller;

import com.AlertOps.model.Team;
import com.AlertOps.service.TeamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    @Autowired
    private TeamService teamService;

    @PostMapping
    public ResponseEntity<?> createTeam(@RequestBody Team team) {
        try {
            Team createdTeam = teamService.createTeam(team);
            return ResponseEntity.ok(createdTeam);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error creating team: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTeam(@PathVariable Long id) {
        try {
            Team team = teamService.getTeam(id);
            return ResponseEntity.ok(team);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching team: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/invite")
    public ResponseEntity<?> inviteUser(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        try {
            String email = payload.get("email");
            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest().body("Email is required");
            }
            teamService.createInvitation(id, email);
            return ResponseEntity.ok("Invitation sent successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error inviting user: " + e.getMessage());
        }
    }

    @PostMapping("/join")
    public ResponseEntity<?> joinTeam(@RequestParam String token, @RequestBody Map<String, String> payload) {
        try {
            String email = payload.get("email"); // Or get from authenticated user context
            teamService.joinTeam(token, email);
            return ResponseEntity.ok("Joined team successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error joining team: " + e.getMessage());
        }
    }
}
