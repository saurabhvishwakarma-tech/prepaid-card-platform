package com.cardplatform.core.support;
import java.security.SecureRandom;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureTestGrpcTransport
public abstract class IntegrationTestSupport {

    protected static final String PASSWORD = "correct-horse-battery";

    // Generated once per run: the card keys must stay the same for the whole test context
    private static final String PAN_ENCRYPTION_KEY = randomKey();
    private static final String PAN_HASH_KEY = randomKey();
    private static final String CVV_KEY = randomKey();
    protected static final String NETWORK_CLIENT_ID = "test-network";
    protected static final String NETWORK_CLIENT_SECRET = randomKey();

    // One container for the whole test run; every test class shares it and the same Spring context
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException, NoSuchAlgorithmException {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // A fresh key pair per test run, so no private key is ever committed for tests
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        Path dir = Files.createTempDirectory("jwt-keys");
        Path privateKey = writePem(dir.resolve("private.pem"), "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        Path publicKey = writePem(dir.resolve("public.pem"), "PUBLIC KEY", keyPair.getPublic().getEncoded());
        registry.add("cardplatform.jwt.private-key-location", () -> privateKey.toUri().toString());
        registry.add("cardplatform.jwt.public-key-location", () -> publicKey.toUri().toString());

        registry.add("cardplatform.card.pan-encryption-key", () -> PAN_ENCRYPTION_KEY);
        registry.add("cardplatform.card.pan-hash-key", () -> PAN_HASH_KEY);
        registry.add("cardplatform.card.cvv-key", () -> CVV_KEY);
        registry.add("cardplatform.network.client-id", () -> NETWORK_CLIENT_ID);
        registry.add("cardplatform.network.client-secret", () -> NETWORK_CLIENT_SECRET);    }

    protected String verifiedCustomerToken() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, PASSWORD)).andExpect(status().isCreated());
        String token = loginAndGetToken(email);
        mockMvc.perform(post("/api/core/users/me/kyc").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return token;
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Autowired
    private WebApplicationContext context;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    protected String loginAndGetToken(String email) throws Exception {
        String body = mockMvc.perform(login(email, PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    protected static MockHttpServletRequestBuilder register(String email, String password) {
        return post("/api/core/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "fullName": "Test User"}
                        """.formatted(email, password));
    }

    protected static MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/core/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password));
    }

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static Path writePem(Path file, String type, byte[] der) throws IOException {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return Files.writeString(file, "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
    }
}