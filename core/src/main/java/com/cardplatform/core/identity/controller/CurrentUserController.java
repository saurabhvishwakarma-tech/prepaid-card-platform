package com.cardplatform.core.identity.controller;

import com.cardplatform.core.identity.dto.UserResponse;
import com.cardplatform.core.identity.mapper.UserMapper;
import com.cardplatform.core.identity.service.IdentityService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/core/users/me")
public class CurrentUserController {

    private final IdentityService identityService;
    private final UserMapper userMapper;

    public CurrentUserController(IdentityService identityService, UserMapper userMapper) {
        this.identityService = identityService;
        this.userMapper = userMapper;
    }

    @GetMapping
    public UserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        return userMapper.toResponse(identityService.getUser(userId(jwt)));
    }

    // Simulated KYC: a real provider would check identity documents before this
    @PostMapping("/kyc")
    public UserResponse completeKyc(@AuthenticationPrincipal Jwt jwt) {
        return userMapper.toResponse(identityService.completeKyc(userId(jwt)));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}