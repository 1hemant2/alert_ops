package com.alertops.team.controller;

import com.alertops.caching.IntentCache;
import com.alertops.team.dto.InviteDtoReq;
import com.alertops.team.dto.TeamReqDto;
import com.alertops.team.service.TeamService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/team")
public class TeamController {

    TeamService teamService;
    IntentCache intentCache;
    TeamController(TeamService teamService ,
                   IntentCache intentCache
                   ) {
        this.teamService = teamService;
        this.intentCache = intentCache;
    }

    @GetMapping("/select")
    public ResponseEntity<?> selectTeam(@RequestParam  UUID teamId) {
        try {
            Map<String, Object> res = teamService.selectTeam(teamId);
            return ResponseEntity.ok( Map.of("data", res));
        } catch (RuntimeException e) {
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error occured while user login" + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> createTeam(@RequestBody TeamReqDto req) {
        try {
            return ResponseEntity.ok(teamService.createTeam(req.getTeamName()));
        } catch (RuntimeException e) {
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error occured while user login" + e.getMessage());
        }
    }

    @DeleteMapping
    public ResponseEntity<?> deleteTeam() {
        try {
            return ResponseEntity.ok("");
        } catch (RuntimeException e) {
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error occured while user login" + e.getMessage());
        }
    }

    //do at the end
    @DeleteMapping("/delete-memeber")
    public ResponseEntity<?> deleteTeamMember() {
        try {
            return ResponseEntity.ok("");
        } catch (RuntimeException e) {
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error occured while user login" + e.getMessage());
        }
    }

    @DeleteMapping("/update-role")
    public ResponseEntity<?> updateRole() {
        try {
            return ResponseEntity.ok("");
        } catch (RuntimeException e) {
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error occured while user login" + e.getMessage());
        }
    }

    @PostMapping("/invite")
    public ResponseEntity<?> inviteUser(@RequestBody InviteDtoReq req) {
        try {
            teamService.inviteUser(req);
            return ResponseEntity.ok("user invited successfully");
        } catch (RuntimeException e) {
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error occured while user login" + e.getMessage());
        }
    }

    @GetMapping("/join")
    public ResponseEntity<?> joinTeam(@RequestParam UUID token) {
        try {
            return ResponseEntity.ok(teamService.joinTeam(token));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            Map.of(
                                    "action", "ERROR",
                                    "message", e.getMessage()
                            )
                    );
        }
    }


    @GetMapping
    public ResponseEntity<?> getUserTeams() {
        try {
            return ResponseEntity.ok(teamService.getUserTeams());
        } catch (RuntimeException e) {
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error occured while fetching user teams" + e.getMessage());
        }
    }

     /*   @GetMapping("/user")
    public  ResponseEntity<?> getUser(@RequestParam(required = false) Long id,
                                      @RequestParam(required = false) String userName,
                                      @RequestParam(required = false) String email) {
        try {
            return  ResponseEntity.ok(userService.getUser(id, userName, email));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("⚠️ Could not fetch user: " + e.getMessage());
        }
    }*/


 /*   @PostMapping(value = "/user", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE}) //This consume both text and json content type
    public ResponseEntity<?> createUser(@RequestBody User user) {
        try {
            User createdUser = userService.createUser(user);
            return ResponseEntity.ok(createdUser); // 200 with body
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("⚠️ Could not create user: " + e.getMessage());
        }
    }*/

/*    @PutMapping("/user")
    public ResponseEntity<?> updateUser(@RequestBody UpdateUserEmailByEmail request) {
        try {
            String  email = request.getEmail();
            String newEmail = request.getNewEmail();
            userService.updateUserEmail(email, newEmail);
            UserDto newUser = userService.getUser(null, null,email);
            return ResponseEntity.ok(newUser); // 200 with body
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("⚠️ Could not create user: " + e.getMessage());
        }
    }*/



/*    @GetMapping("/get-user-role")
    public  ResponseEntity<?> getUserRole(@RequestParam Long userId) {
        try {
            Set<Role> roles = userService.getUserRole(userId);
            return  ResponseEntity.ok(roles);
        } catch (RuntimeException e) {
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error occured while updating user role" + e.getMessage());
        }
    }*/

/*    @GetMapping("/update-user-role")
    public  ResponseEntity<?> updateUserRole(@RequestBody UpdateUserRole req) {
        try {
            Long userId = req.getUserId();
            String existingRole = req.getExistingRole();
            String newRole = req.getNewRole();
            userService.updateUserRole(userId, existingRole, newRole);
            return ResponseEntity.ok("user role updated");
        } catch (RuntimeException e) {
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error occured while updating user role" + e.getMessage());
        }
    }*/

/*    @DeleteMapping("/delete-user-role")
    public  ResponseEntity<?> deleteUserRole(@RequestBody DeleteUserRole req) {
        try {
            Long userId = req.getUserId();
            String roleName = req.getUserRole();
            userService.deleteUserRole(userId, roleName);
            return ResponseEntity.ok("user role updated");
        } catch (RuntimeException e) {
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error occured while updating user role" + e.getMessage());
        }
    }*/

/*    @PostMapping("/add-user-role")
    public  ResponseEntity<?> addeUserRole(@RequestBody AddUserRole req) {
        try {
            Long userId = req.getUserId();
            String roleName = req.getRoleName();
            userService.addUserRole(userId, roleName);
            return ResponseEntity.ok("user role updated");
        } catch (RuntimeException e) {
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error occured while updating user role" + e.getMessage());
        }
    }*/
}
