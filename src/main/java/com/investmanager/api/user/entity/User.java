package com.investmanager.api.user.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY )
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Generated
    @Column(name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false)
    private LocalDateTime createdAt;

    public User() {
    }

    public User(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
