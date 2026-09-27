package com.alertops.auth.controller;

import com.alertops.auth.dto.UserRegisterDto;
import com.alertops.auth.dto.UserResponseDto;
import com.alertops.auth.model.User;
import com.alertops.auth.service.UserService;
import com.alertops.caching.IntentCache;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserControllerTest {
    @Test
    void registerReturnsOnlyPublicUserFields() {
        UserService userService = mock(UserService.class);
        IntentCache intentCache = mock(IntentCache.class);
        User user = mock(User.class);
        UUID id = UUID.randomUUID();
        Date createdAt = new Date();
        when(user.getId()).thenReturn(id);
        when(user.getName()).thenReturn("Avery");
        when(user.getEmail()).thenReturn("avery@example.com");
        when(user.getCreatedAt()).thenReturn(createdAt);
        when(userService.createUser(org.mockito.ArgumentMatchers.any())).thenReturn(user);

        var response = new UserController(userService, intentCache).registerUser(new UserRegisterDto());

        assertEquals(201, response.getStatusCode().value());
        UserResponseDto body = assertInstanceOf(UserResponseDto.class, response.getBody());
        assertEquals(id, body.id());
        assertEquals("Avery", body.name());
        assertEquals("avery@example.com", body.email());
        assertEquals(createdAt, body.createdAt());
    }
}
