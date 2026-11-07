package com.cardplatform.core.identity.controller;

import com.cardplatform.core.identity.dto.LoginRequest;
import com.cardplatform.core.identity.dto.RegisterRequest;
import com.cardplatform.core.identity.dto.TokenResponse;
import com.cardplatform.core.identity.dto.UserResponse;
import com.cardplatform.core.identity.mapper.UserMapper;
import com.cardplatform.core.identity.service.AccessToken;
import com.cardplatform.core.identity.service.IdentityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/core/auth")
public class AuthController {

    private final IdentityService identityService;
    private final UserMapper userMapper;

    public AuthController(IdentityService identityService, UserMapper userMapper) {
        this.identityService = identityService;
        this.userMapper = userMapper;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userMapper.toResponse(identityService.register(request));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        AccessToken token = identityService.login(request);
        return new TokenResponse(token.value(), "Bearer", token.ttl().toSeconds());
    }
}