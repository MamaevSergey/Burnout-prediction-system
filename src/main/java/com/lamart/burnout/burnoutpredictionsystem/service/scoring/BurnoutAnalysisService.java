package com.lamart.burnout.burnoutpredictionsystem.service.scoring;

import com.lamart.burnout.burnoutpredictionsystem.dto.EmployeeDetailDto;
import com.lamart.burnout.burnoutpredictionsystem.dto.EmployeeSummaryDto;
import com.lamart.burnout.burnoutpredictionsystem.entity.BurnoutScore;
import com.lamart.burnout.burnoutpredictionsystem.entity.DailyMetric;
import com.lamart.burnout.burnoutpredictionsystem.repository.BurnoutScoreRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.DailyMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BurnoutAnalysisService {

    private final BurnoutScoreRepository burnoutScoreRepository;
    private final DailyMetricRepository dailyMetricRepository;

    public List<EmployeeSummaryDto> getAllEmployeesSummary() {
        return burnoutScoreRepository.findLatestScores().stream()
                .map(score -> new EmployeeSummaryDto(
                        score.getEmployee().getId(),
                        score.getEmployee().getTeam().getName(),
                        score.getEmployee().getRole(),
                        score.getRiskProbability(),
                        score.getStatusColor()
                ))
                .collect(Collectors.toList());
    }

    public EmployeeDetailDto getEmployeeDetail(UUID employeeId) {
        BurnoutScore score = burnoutScoreRepository
                .findFirstByEmployeeIdOrderByCalculatedAtDesc(employeeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Данные для сотрудника не найдены: " + employeeId));

        List<DailyMetric> metrics = dailyMetricRepository.findAllByEmployeeIdAndDateAfter(employeeId, LocalDate.now().minusDays(30));

        int totalWorkHours = metrics.stream().mapToInt(DailyMetric::getTotalWorkSeconds).sum() / 3600;
        int totalNightHours = metrics.stream().mapToInt(DailyMetric::getNightWorkSeconds).sum() / 3600;

        return new EmployeeDetailDto(
                score.getEmployee().getId(),
                score.getEmployee().getTeam().getName(),
                score.getEmployee().getRole(),
                score.getRiskProbability(),
                score.getStatusColor(),
                score.getEeIndex(),
                score.getDpIndex(),
                score.getRpaIndex(),
                totalWorkHours,
                totalNightHours
        );
    }
}
