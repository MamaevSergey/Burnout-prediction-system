package com.lamart.burnout.burnoutpredictionsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "git_pull_requests")
@Getter @Setter @NoArgsConstructor
public class GitPullRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", unique = true)
    private String externalId;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private LocalDateTime createdAt;
    private LocalDateTime mergedAt;
    private Integer leadTimeMinutes;
}