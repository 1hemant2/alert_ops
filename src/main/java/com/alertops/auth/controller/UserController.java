package com.alertops.auth.controller;

import com.alertops.auth.dto.*;
import com.alertops.auth.service.UserService;
import com.alertops.caching.IntentCache;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class UserController {

    private final UserService userService;
    private  final  IntentCache intentCache;

    public UserController(UserService userService,
                          IntentCache intentCache
                          )
    {
        this.userService = userService;
        this.intentCache = intentCache;
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody UserLoginDto req) {
        try {

            Map<String, Object> res = userService.Login(req);
            UUID intentId = req.getIntentId();

            if(intentId != null && intentCache.get(intentId) != null) {
                return ResponseEntity.ok(
                        Map.of(
                                "action", "TEAM_SELECTION_REQUIRED",
                                "nextPath", "/api/v1/team/join",
                                "data", res,
                                "authenticated", true
                        )
                );
            }

            return ResponseEntity.ok(
                    Map.of(
                            "authenticated", true,
                            "data", res
                    )
            );
        } catch (RuntimeException e) {
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error occured while user login" + e.getMessage());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody UserRegisterDto req) {
        try {
            return  ResponseEntity.ok(userService.createUser(req));
        } catch (RuntimeException e) {
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error occured while user register" + e.getMessage());
        }
    }

    @DeleteMapping("/user")
    public ResponseEntity<?> deleteUser(@RequestBody UserDto req) {
        try {
            String email = req.getEmail();
            Long id = req.getId();
            boolean isDeleted = userService.deleteUser(id, email);
            return isDeleted
                    ? ResponseEntity.ok("User successfully deleted.")
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to delete user.");
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("⚠️ Could not create user: " + e.getMessage());
        }
    }
}

