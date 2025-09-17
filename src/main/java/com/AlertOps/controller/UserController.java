package com.AlertOps.controller;

import com.AlertOps.dto.user.*;
import com.AlertOps.model.Role;
import  com.AlertOps.model.User;
import com.AlertOps.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;


    public UserController(UserService userService) {
        this.userService = userService;
    }


    @GetMapping("/user")
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
    }

    @GetMapping("/users")
    public  ResponseEntity<?> getAllUser() {
        try {
            return  ResponseEntity.ok(userService.getAllUser());
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("⚠️ Could not fetch user: " + e.getMessage());
        }
    }

    @PostMapping(value = "/user", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE}) //This consume both text and json content type
    public ResponseEntity<?> createUser(@RequestBody User user) {
        try {
            User createdUser = userService.createUser(user);
            return ResponseEntity.ok(createdUser); // 200 with body
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("⚠️ Could not create user: " + e.getMessage());
        }
    }

    @PutMapping("/user")
    public ResponseEntity<?> updateUser(@RequestBody UpdateUserEmailByEmail request) {
        try {
            String  email = request.getEmail();
            String newEmail = request.getNewEmail();
            userService.updateUserEmail(email, newEmail);
            User newUser = userService.getUser(null, null,email);
            return ResponseEntity.ok(newUser); // 200 with body
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("⚠️ Could not create user: " + e.getMessage());
        }
    }

    @DeleteMapping("/user")
    public ResponseEntity<?> deleteUser(@RequestBody GetUser req) {
        try {
            String email = req.getEmail();
            String userName = req.getUserName();
            Long id = req.getId();
            boolean isDeleted = userService.deleteUser(id, userName, email);
            return isDeleted
                    ? ResponseEntity.ok("User successfully deleted.")
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to delete user.");
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("⚠️ Could not create user: " + e.getMessage());
        }
    }

    @GetMapping("/get-user-role")
    public  ResponseEntity<?> getUserRole(@RequestParam Long userId) {
        try {
            Set<Role> roles = userService.getUserRole(userId);
            return  ResponseEntity.ok(roles);
        } catch (RuntimeException e) {
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error occured while updating user role" + e.getMessage());
        }
    }

    @GetMapping("/update-user-role")
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
    }

    @DeleteMapping("/delete-user-role")
    public  ResponseEntity<?> deleteUserRole(@RequestBody DeleteUserRole req) {
        try {
            Long userId = req.getUserId();
            String roleName = req.getUserRole();
            userService.deleteUserRole(userId, roleName);
            return ResponseEntity.ok("user role updated");
        } catch (RuntimeException e) {
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error occured while updating user role" + e.getMessage());
        }
    }

    @PostMapping("/add-user-role")
    public  ResponseEntity<?> addeUserRole(@RequestBody AddUserRole req) {
        try {
            Long userId = req.getUserId();
            String roleName = req.getRoleName();
            userService.addUserRole(userId, roleName);
            return ResponseEntity.ok("user role updated");
        } catch (RuntimeException e) {
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error occured while updating user role" + e.getMessage());
        }
    }

}

