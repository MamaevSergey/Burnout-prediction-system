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
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "team_id")
    private Team team;

    private String role;

    @Column(name = "github_username", unique = true)
    private String githubUsername;

    private boolean isActive = true;

    @Column(name = "timezone")
    private String timezone = "Asia/Yekaterinburg";
}
