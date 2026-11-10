package com.cardplatform.core.payments.config;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

// Checks the card network's client credentials on every gRPC call
class NetworkClientAuthenticationProvider implements AuthenticationProvider {

    private final String clientId;
    private final byte[] clientSecret;

    NetworkClientAuthenticationProvider(NetworkClientProperties properties) {
        this.clientId = properties.clientId();
        this.clientSecret = properties.clientSecret().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        Object credentials = authentication.getCredentials();
        byte[] presentedSecret = credentials == null
                ? new byte[0]
                : credentials.toString().getBytes(StandardCharsets.UTF_8);

        boolean idMatches = clientId.equals(authentication.getName());
        // Constant time, so how long a wrong guess takes reveals nothing about the secret
        boolean secretMatches = MessageDigest.isEqual(clientSecret, presentedSecret);
        if (!idMatches || !secretMatches) {
            throw new BadCredentialsException("Invalid network credentials");
        }
        return UsernamePasswordAuthenticationToken.authenticated(
                authentication.getName(), null, List.of(new SimpleGrantedAuthority("ROLE_NETWORK")));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}