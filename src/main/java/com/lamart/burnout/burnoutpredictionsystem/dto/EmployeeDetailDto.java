package com.lamart.burnout.burnoutpredictionsystem.dto;

import java.util.UUID;

public record EmployeeDetailDto(
        UUID employeeId,
        String teamName,
        String role,
        Double riskProbability,
        String statusColor,
        Double eeIndex,
        Double dpIndex,
        Double rpaIndex,
        Integer totalWorkHours,
        Integer nightWorkHours
) {}