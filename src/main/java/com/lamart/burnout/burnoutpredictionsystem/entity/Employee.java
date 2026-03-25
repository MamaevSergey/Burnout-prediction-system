package com.lamart.burnout.burnoutpredictionsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "employees")
@Getter @Setter @NoArgsConstructor
public class Employee {
    @Id
    private UUID id; // Хэшированный ID

    @Column(name = "github_username", unique = true)
    private String githubUsername;

    @ManyToOne
    @JoinColumn(name = "team_id")
    private Team team;

    private String role;
    private boolean isActive = true;
}
