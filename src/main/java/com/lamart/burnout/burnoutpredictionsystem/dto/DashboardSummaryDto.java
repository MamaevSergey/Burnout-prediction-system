package com.lamart.burnout.burnoutpredictionsystem.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardSummaryDto {
    private GlobalStatsDto stats;
    private List<EmployeeRiskDto> employees;

    @Data
    @Builder
    public static class GlobalStatsDto {
        private int totalEmployees;
        private String activeModelVersion;
        private String nextEtlRun;
        private String lastEtlRun;
        private int averageRiskPercentYesterday;
        private int averageRiskPercent;
        private boolean averageRiskTrendUp;
        private int highRiskCount;
    }

    @Data
    @Builder
    public static class EmployeeRiskDto {
        private String employeeId;
        private double riskProbability;
        private String statusColor;
        private String teamName;
        private String lastSync;
    }
}
