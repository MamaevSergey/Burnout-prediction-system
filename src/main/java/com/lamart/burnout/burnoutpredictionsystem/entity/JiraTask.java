package com.lamart.burnout.burnoutpredictionsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "jira_tasks")
@Getter @Setter @NoArgsConstructor
public class JiraTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long internalId; // Системный Primary Key

    @Column(name = "external_id", unique = true)
    private String externalId;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;

    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}