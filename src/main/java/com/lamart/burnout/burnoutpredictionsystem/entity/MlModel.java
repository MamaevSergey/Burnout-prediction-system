package com.lamart.burnout.burnoutpredictionsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ml_models")
@Getter @Setter @NoArgsConstructor
public class MlModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String version;

    private LocalDateTime trainedAt;
    private Double w0Bias;
    private Double w1Ee;
    private Double w2Dp;
    private Double w3Rpa;
    private boolean isActive;
}
