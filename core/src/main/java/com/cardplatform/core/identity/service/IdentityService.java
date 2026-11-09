package com.cardplatform.core.identity.service;

import com.cardplatform.core.identity.api.IdentityApi;
import com.cardplatform.core.identity.api.UserSummary;
import com.cardplatform.core.identity.dto.LoginRequest;
import com.cardplatform.core.identity.dto.RegisterRequest;
import com.cardplatform.core.identity.exception.EmailAlreadyRegisteredException;
import com.cardplatform.core.identity.exception.InvalidCredentialsException;
import com.cardplatform.core.identity.exception.UserNotFoundException;
import com.cardplatform.core.identity.mapper.UserMapper;
import com.cardplatform.core.identity.model.User;
import com.cardplatform.core.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cardplatform.core.identity.api.UserRegisteredEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdentityService implements IdentityApi {

    private static final Logger log = LoggerFactory.getLogger(IdentityService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher events;
    // Checked when the email is unknown, so a failed login costs the same time either way
    private final String dummyPasswordHash;

    public IdentityService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           TokenService tokenService, UserMapper userMapper,
                           ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.userMapper = userMapper;
        this.events = events;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = normalise(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = User.newCustomer(email, passwordEncoder.encode(request.password()), request.fullName().strip());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at once: the unique constraint stops the second
            throw new EmailAlreadyRegisteredException();
        }
        events.publishEvent(new UserRegisteredEvent(user.getId()));
        log.info("User registered: userId={}", user.getId());
        return user;
    }

    @Transactional(readOnly = true)
    public AccessToken login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(normalise(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        log.info("User logged in: userId={}", user.get().getId());
        return tokenService.issueAccessToken(user.get());
    }

    @Transactional(readOnly = true)
    public User getUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    @Transactional
    public User completeKyc(UUID userId) {
        User user = getUser(userId);
        user.markKycVerified();
        log.info("KYC verified: userId={}", userId);
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> findUser(UUID userId) {
        return userRepository.findById(userId).map(userMapper::toSummary);
    }

    private static String normalise(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}