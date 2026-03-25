package com.lamart.burnout.burnoutpredictionsystem.dto;

import java.util.UUID;

public record EmployeeSummaryDto(
        UUID employeeId,
        String teamName,
        String role,
        Double riskProbability,
        String statusColor
) {}