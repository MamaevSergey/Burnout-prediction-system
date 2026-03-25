package com.lamart.burnout.burnoutpredictionsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "git_commits")
@Getter @Setter @NoArgsConstructor
public class GitCommit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long internalId;

    @Column(name = "external_hash", unique = true)
    private String externalHash;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private LocalDateTime committedAt;
    private Integer messageLength;
}
