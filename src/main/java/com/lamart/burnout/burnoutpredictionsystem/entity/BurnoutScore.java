package com.lamart.burnout.burnoutpredictionsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "burnout_scores")
@Getter @Setter @NoArgsConstructor
public class BurnoutScore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "model_id")
    private MlModel model;

    private LocalDateTime calculatedAt;

    private Double eeIndex;
    private Double dpIndex;
    private Double rpaIndex;
    private Double riskProbability;

    private String statusColor;
}