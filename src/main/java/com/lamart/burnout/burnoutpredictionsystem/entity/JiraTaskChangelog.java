package com.lamart.burnout.burnoutpredictionsystem.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "jira_task_changelogs")
@Data
public class JiraTaskChangelog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private JiraTask task;

    private String fieldName;
    private String fromString;
    private String toString;
    private LocalDateTime createdAt;
}
