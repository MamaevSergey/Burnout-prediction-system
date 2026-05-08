package com.lamart.burnout.burnoutpredictionsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "daily_metrics")
@Getter @Setter @NoArgsConstructor
public class DailyMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private LocalDate date;

    // EE
    private long activitySpanSeconds;
    private int nightEventsCount;
    private int weekendEventsCount;

    // DP
    private double badCommitRatio;
    private int jiraEffortScore;

    // RPA
    private double prLeadTimeAvgMinutes;
    private long taskStagnationSeconds;
    private double reopenRate;
    private int mergeConflictsCount;
}
