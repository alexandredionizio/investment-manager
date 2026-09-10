package com.investmanager.api.portfolio;

import com.investmanager.api.user.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "portfolios")
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public Portfolio(String name,
                     String description,
                     User user) {

        this.name = name;
        this.description = description;
        this.user = user;
        this.createdAt = LocalDateTime.now();
    }

    public void initializeCreatedAt() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Portfolio() {

    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
