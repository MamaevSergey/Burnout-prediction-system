package com.lamart.burnout.burnoutpredictionsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@AllArgsConstructor
public class EmployeeDetailDto {
    private UUID employeeId;
    private String teamName;
    private String role;
    private double riskProbability;
    private String statusColor;
    private double eeIndex;
    private double dpIndex;
    private double rpaIndex;

    // Журнал оперативной деятельности
    private double prLeadTimeHours;
    private boolean weekendWork;
    private boolean nightWork;
    private double reopenRatePercent;
    private double commitsPerDay;
    private boolean commitsTrendUp;

    // Footer Info
    private String modelVersion;
    private LocalDateTime lastUpdatedAt;

    private Map<String, String> indexInterpretations;
}