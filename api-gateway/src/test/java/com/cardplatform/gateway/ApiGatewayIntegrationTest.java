package com.cardplatform.gateway;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayIntegrationTest {

    private static final String ISSUER = "card-platform-core";

    // Same key ID on purpose: the forged token must fail on its signature, not just on an unknown key ID
    private static final RSAKey CORE_KEY = generateKey("core-key");
    private static final RSAKey FORGED_KEY = generateKey("core-key");

    private static final FakeCore CORE = FakeCore.start(CORE_KEY);
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8.8.1")).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("cardplatform.gateway.core-uri", CORE::baseUrl);
        registry.add("cardplatform.gateway.jwk-set-uri", () -> CORE.baseUrl() + "/.well-known/jwks.json");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private Environment environment;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + environment.getProperty("local.server.port"))
                .build();
        CORE.reset();
    }

    @Test
    void requestWithoutATokenNeverReachesCore() {
        client.get().uri("/api/core/accounts/me").exchange().expectStatus().isUnauthorized();

        assertThat(CORE.hits()).isZero();
    }

    @Test
    void validTokenIsForwardedToCoreUnchanged() {
        String token = token(CORE_KEY, ISSUER, UUID.randomUUID().toString());

        client.get().uri("/api/core/accounts/me").header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isOk();

        assertThat(CORE.hits()).isEqualTo(1);
        // Core checks the token again itself, so it must arrive as sent
        assertThat(CORE.lastAuthorization()).isEqualTo("Bearer " + token);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String forged = token(FORGED_KEY, ISSUER, UUID.randomUUID().toString());

        client.get().uri("/api/core/accounts/me").header("Authorization", "Bearer " + forged)
                .exchange().expectStatus().isUnauthorized();

        assertThat(CORE.hits()).isZero();
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String foreign = token(CORE_KEY, "someone-else", UUID.randomUUID().toString());

        client.get().uri("/api/core/accounts/me").header("Authorization", "Bearer " + foreign)
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void loginNeedsNoTokenButIsRateLimitedPerClient() {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            statuses.add(client.post().uri("/api/core/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{}")
                    .exchange()
                    .returnResult(Void.class)
                    .getStatus()
                    .value());
        }

        assertThat(statuses.getFirst()).isEqualTo(200);
        assertThat(statuses).contains(429);
    }

    @Test
    void eachUserHasTheirOwnLimit() {
        String busyUser = token(CORE_KEY, ISSUER, UUID.randomUUID().toString());
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            statuses.add(client.get().uri("/api/core/accounts/me")
                    .header("Authorization", "Bearer " + busyUser)
                    .exchange()
                    .returnResult(Void.class)
                    .getStatus()
                    .value());
        }
        assertThat(statuses).contains(429);

        // A different user still gets through: buckets are per user, not shared
        String otherUser = token(CORE_KEY, ISSUER, UUID.randomUUID().toString());
        client.get().uri("/api/core/accounts/me").header("Authorization", "Bearer " + otherUser)
                .exchange().expectStatus().isOk();
    }

    private static String token(RSAKey key, String issuer, String userId) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId)
                .issuer(issuer)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("roles", List.of("CUSTOMER"))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        try {
            jwt.sign(new RSASSASigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    private static RSAKey generateKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    // Stands in for core: publishes the JWKS and answers any /api/core call with 200
    private static final class FakeCore {

        private final HttpServer server;
        private final AtomicInteger hits = new AtomicInteger();
        private final AtomicReference<String> lastAuthorization = new AtomicReference<>();

        private FakeCore(HttpServer server) {
            this.server = server;
        }

        static FakeCore start(RSAKey signingKey) {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                FakeCore core = new FakeCore(server);
                byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                server.createContext("/.well-known/jwks.json", exchange -> respond(exchange, jwks));
                server.createContext("/api/core", exchange -> {
                    core.hits.incrementAndGet();
                    core.lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    respond(exchange, "{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
                });
                server.start();
                return core;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static void respond(HttpExchange exchange, byte[] body) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        int hits() {
            return hits.get();
        }

        String lastAuthorization() {
            return lastAuthorization.get();
        }

        void reset() {
            hits.set(0);
            lastAuthorization.set(null);
        }
    }
}