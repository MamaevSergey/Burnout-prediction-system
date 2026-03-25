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
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private LocalDate date;

    // EE
    private int nightWorkSeconds;
    private int weekendWorkSeconds;
    private int totalWorkSeconds;

    // DP
    private int avgCommitMsgLen;
    private int jiraCommentsCount;

    // RPA
    private int prLeadTimeAvg;
    private int reopenRate;
    private int taskStagnationSeconds;
}
