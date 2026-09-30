package com.alertops.auth.controller;

import com.alertops.auth.dto.*;
import com.alertops.auth.service.EmailVerificationExpiredException;
import com.alertops.auth.service.EmailVerificationInvalidException;
import com.alertops.auth.service.EmailVerificationMailDeliveryException;
import com.alertops.auth.service.EmailVerificationRequiredException;
import com.alertops.auth.service.UserService;
import com.alertops.caching.Intent;
import com.alertops.caching.IntentCache;
import com.alertops.caching.IntentType;
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
    public ResponseEntity<?> loginUser(@RequestBody UserLoginDto loginRequest) {
        try {

            Map<String, Object> loginData = userService.login(loginRequest);
            UUID intentId = loginRequest.getIntentId();
            Intent intent = intentCache.consume(intentId);

            if (intent != null && intent.type() == IntentType.JOIN_TEAM) {
                return ResponseEntity.ok(
                        Map.of(
                                "action", "TEAM_SELECTION_REQUIRED",
                                "nextPath", "/api/v1/team/join",
                                "data", loginData,
                                "authenticated", true
                        )
                );
            }

            return ResponseEntity.ok(
                    Map.of(
                            "authenticated", true,
                            "data", loginData
                    )
            );
        } catch (EmailVerificationRequiredException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("code", "EMAIL_NOT_VERIFIED", "message", e.getMessage()));
        } catch (RuntimeException e) {
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error occured while user login" + e.getMessage());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody UserRegisterDto registrationRequest) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(UserResponseDto.from(userService.createUser(registrationRequest)));
        } catch (EmailVerificationMailDeliveryException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("code", "EMAIL_DELIVERY_FAILED", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_REGISTRATION", "message", e.getMessage()));
        } catch (RuntimeException e) {
            return  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error occured while user register" + e.getMessage());
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmailAddress(@RequestBody EmailVerificationRequest verificationRequest) {
        try {
            userService.verifyEmailAddress(verificationRequest == null ? null : verificationRequest.token());
            return ResponseEntity.ok(Map.of(
                    "emailVerified", true,
                    "message", "Your email address is verified."
            ));
        } catch (EmailVerificationExpiredException e) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(Map.of("code", "VERIFICATION_EXPIRED", "message", e.getMessage()));
        } catch (EmailVerificationInvalidException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "VERIFICATION_INVALID", "message", e.getMessage()));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerificationEmail(
            @RequestBody EmailVerificationResendRequest resendRequest) {
        try {
            userService.resendVerificationEmail(resendRequest == null ? null : resendRequest.email());
            return ResponseEntity.ok(Map.of(
                "message", "If your account needs verification, a new email has been sent."
            ));
        } catch (EmailVerificationMailDeliveryException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("code", "EMAIL_DELIVERY_FAILED", "message", e.getMessage()));
        }
    }

}
