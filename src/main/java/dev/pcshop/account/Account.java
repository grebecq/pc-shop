package dev.pcshop.account;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Instant;

/** Аккаунт покупателя на сайте. */
@Entity
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String passwordHash;
    private String name;
    private String phone;
    private Instant createdAt = Instant.now();

    protected Account() {
    }

    Account(String email, String passwordHash, String name) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public Instant getCreatedAt() { return createdAt; }

    void setName(String name) { this.name = name; }
    void setPhone(String phone) { this.phone = phone; }
    void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
