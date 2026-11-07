package com.cardplatform.core.identity.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "users")
public class User {

    @Id
    private UUID id;

    private String email;

    private String passwordHash;

    private String fullName;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private KycStatus kycStatus;

    private Instant createdAt;

    private Instant updatedAt;

    // Wrapper type: null tells Spring Data the entity is new, so save() inserts instead of merging
    @Version
    private Long version;

    protected User() {
    }

    public static User newCustomer(String email, String passwordHash, String fullName) {
        User user = new User();
        user.id = UUID.randomUUID();
        user.email = email;
        user.passwordHash = passwordHash;
        user.fullName = fullName;
        user.role = Role.CUSTOMER;
        user.kycStatus = KycStatus.PENDING;
        user.createdAt = now();
        user.updatedAt = user.createdAt;
        return user;
    }

    public void markKycVerified() {
        if (kycStatus != KycStatus.VERIFIED) {
            kycStatus = KycStatus.VERIFIED;
            updatedAt = now();
        }
    }

    // Postgres stores microseconds; truncating keeps the value in memory equal to the stored one
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}