package com.cardplatform.core.identity;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class IdentityIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    // A fresh key pair per test run, so no private key is ever committed for tests
    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) throws IOException, NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        Path dir = Files.createTempDirectory("jwt-keys");
        Path privateKey = writePem(dir.resolve("private.pem"), "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        Path publicKey = writePem(dir.resolve("public.pem"), "PUBLIC KEY", keyPair.getPublic().getEncoded());
        registry.add("cardplatform.jwt.private-key-location", () -> privateKey.toUri().toString());
        registry.add("cardplatform.jwt.public-key-location", () -> publicKey.toUri().toString());
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void customerCanRegisterLogInAndCompleteKyc() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(register(email, PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.kycStatus").value("PENDING"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String token = loginAndGetToken(email);

        mockMvc.perform(get("/api/core/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(post("/api/core/users/me/kyc").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kycStatus").value("VERIFIED"));
    }

    @Test
    void emailIsUniqueIgnoringCase() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, PASSWORD)).andExpect(status().isCreated());

        mockMvc.perform(register(email.toUpperCase(Locale.ROOT), PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(register(email, PASSWORD)).andExpect(status().isCreated());

        mockMvc.perform(login(email, "wrong-password-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mockMvc.perform(login(uniqueEmail(), PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointRejectsMissingOrInvalidToken() throws Exception {
        mockMvc.perform(get("/api/core/users/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/core/users/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        mockMvc.perform(register("not-an-email", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void jwksPublishesOnlyThePublicKey() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").exists())
                .andExpect(jsonPath("$.keys[0].n").exists())
                // "d" is the private exponent: it must never be published
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    private String loginAndGetToken(String email) throws Exception {
        String body = mockMvc.perform(login(email, PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static MockHttpServletRequestBuilder register(String email, String password) {
        return post("/api/core/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "fullName": "Test User"}
                        """.formatted(email, password));
    }

    private static MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/core/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password));
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static Path writePem(Path file, String type, byte[] der) throws IOException {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return Files.writeString(file, "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
    }
}