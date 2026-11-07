package com.cardplatform.core.identity.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
class JwtConfig {

    @Bean
    RSAKey signingKey(JwtProperties properties) throws IOException, JOSEException {
        RSAPrivateKey privateKey = readKey(properties.privateKeyLocation(), RsaKeyConverters.pkcs8());
        RSAPublicKey publicKey = readKey(properties.publicKeyLocation(), RsaKeyConverters.x509());
        // Fail at startup rather than issue tokens nobody can verify
        if (!privateKey.getModulus().equals(publicKey.getModulus())) {
            throw new IllegalStateException("JWT private and public keys are not a pair");
        }
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyIDFromThumbprint()
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    // Core checks its own tokens too: the gateway is not the only line of defence
    @Bean
    JwtDecoder jwtDecoder(RSAKey signingKey, JwtProperties properties) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    @Bean
    JWKSet publicJwkSet(RSAKey signingKey) {
        return new JWKSet(signingKey.toPublicJWK());
    }

    private static <T> T readKey(Resource resource, Converter<InputStream, T> converter) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return converter.convert(in);
        }
    }
}