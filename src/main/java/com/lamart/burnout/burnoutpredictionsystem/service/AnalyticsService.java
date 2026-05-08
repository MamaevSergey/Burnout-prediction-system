package com.lamart.burnout.burnoutpredictionsystem.service;

import com.lamart.burnout.burnoutpredictionsystem.dto.DashboardSummaryDto;
import com.lamart.burnout.burnoutpredictionsystem.entity.BurnoutScore;
import com.lamart.burnout.burnoutpredictionsystem.entity.MlModel;
import com.lamart.burnout.burnoutpredictionsystem.repository.BurnoutScoreRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.EmployeeRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.MlModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final BurnoutScoreRepository burnoutScoreRepository;
    private final EmployeeRepository employeeRepository;
    private final MlModelRepository mlModelRepository;

    public DashboardSummaryDto getDashboardSummary() {
        long totalEmployees = employeeRepository.count();
        MlModel activeModel = mlModelRepository.findByIsActiveTrue().orElse(null);
        String modelVersion = activeModel != null ? activeModel.getVersion() : "Нет данных";

        LocalDate lastRunDate = burnoutScoreRepository.findMaxTargetDate();

        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime nextRun = nowUtc.toLocalDate().atTime(2, 0).atZone(ZoneOffset.UTC);
        if (nowUtc.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM, HH:mm 'UTC'", Locale.of("ru"));

        DashboardSummaryDto.GlobalStatsDto.GlobalStatsDtoBuilder statsBuilder = DashboardSummaryDto.GlobalStatsDto.builder()
                .totalEmployees((int) totalEmployees)
                .activeModelVersion(modelVersion)
                .nextEtlRun("Следующий будет: " + nextRun.format(formatter));

        if (lastRunDate == null) {
            statsBuilder.averageRiskPercent(0).highRiskCount(0).lastEtlRun("Никогда");
            return DashboardSummaryDto.builder()
                    .stats(statsBuilder.build())
                    .employees(Collections.emptyList())
                    .build();
        }

        statsBuilder.lastEtlRun(lastRunDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.of("ru"))));

        List<BurnoutScore> latestScores = burnoutScoreRepository.findByTargetDate(lastRunDate);
        List<BurnoutScore> previousScores = burnoutScoreRepository.findByTargetDate(lastRunDate.minusDays(1));

        int highRiskCount = 0;
        double totalRisk = 0.0;
        double prevTotalRisk = 0.0;

        for (BurnoutScore score : previousScores) {
            prevTotalRisk += score.getRiskProbability();
        }
        double prevAvg = previousScores.isEmpty() ? 0.0 : prevTotalRisk / previousScores.size();

        List<DashboardSummaryDto.EmployeeRiskDto> employeeDtos = latestScores.stream().map(score -> {
            String teamName = (score.getEmployee().getTeam() != null && score.getEmployee().getTeam().getName() != null)
                    ? score.getEmployee().getTeam().getName()
                    : "Без команды";

            return DashboardSummaryDto.EmployeeRiskDto.builder()
                    .employeeId(score.getEmployee().getId().toString())
                    .riskProbability(score.getRiskProbability())
                    .statusColor(score.getStatusColor())
                    .teamName(teamName)
                    .lastSync(formatLastSync(score.getCalculatedAt()))
                    .build();
        }).collect(Collectors.toList());

        for (BurnoutScore score : latestScores) {
            totalRisk += score.getRiskProbability();
            if (score.getRiskProbability() >= 0.7) highRiskCount++;
        }

        double currentAvg = latestScores.isEmpty() ? 0.0 : totalRisk / latestScores.size();

        statsBuilder.highRiskCount(highRiskCount);
        statsBuilder.averageRiskPercent((int) Math.round(currentAvg * 100));

        statsBuilder.averageRiskPercentYesterday((int) Math.round(prevAvg * 100));
        statsBuilder.averageRiskTrendUp(currentAvg > prevAvg);

        return DashboardSummaryDto.builder()
                .stats(statsBuilder.build())
                .employees(employeeDtos)
                .build();
    }

    private String formatLastSync(LocalDateTime calculatedAt) {
        if (calculatedAt == null) return "Неизвестно";
        long hours = ChronoUnit.HOURS.between(calculatedAt, LocalDateTime.now());
        if (hours == 0) return "Только что";
        if (hours < 24) return hours + " часа назад";
        return calculatedAt.format(DateTimeFormatter.ofPattern("dd MMM, HH:mm 'UTC'", Locale.of("ru")));
    }
}