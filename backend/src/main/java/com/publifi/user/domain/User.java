package com.publifi.user.domain;

import com.publifi.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    private String email;
    private String passwordHash;
    private String displayName;

    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean enabled;
    private Instant emailVerifiedAt;
    private String acceptedTermsVersion;
    private Instant acceptedTermsAt;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    private long version;

    protected User() {
        // JPA
    }

    public User(String email, String passwordHash, String displayName, Role role,
                String acceptedTermsVersion, Instant acceptedTermsAt) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.enabled = true;
        this.acceptedTermsVersion = acceptedTermsVersion;
        this.acceptedTermsAt = acceptedTermsAt;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void markEmailVerified(Instant at) {
        this.emailVerifiedAt = at;
    }

    public void disable() {
        this.enabled = false;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public String getAcceptedTermsVersion() {
        return acceptedTermsVersion;
    }

    public Instant getAcceptedTermsAt() {
        return acceptedTermsAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
