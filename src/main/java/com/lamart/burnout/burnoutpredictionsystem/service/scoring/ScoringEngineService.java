package com.lamart.burnout.burnoutpredictionsystem.service.scoring;

import com.lamart.burnout.burnoutpredictionsystem.entity.BurnoutScore;
import com.lamart.burnout.burnoutpredictionsystem.entity.DailyMetric;
import com.lamart.burnout.burnoutpredictionsystem.entity.Employee;
import com.lamart.burnout.burnoutpredictionsystem.entity.MlModel;
import com.lamart.burnout.burnoutpredictionsystem.repository.BurnoutScoreRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.DailyMetricRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.EmployeeRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.MlModelRepository;
import com.lamart.burnout.burnoutpredictionsystem.util.MathUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoringEngineService {
    private final EmployeeRepository employeeRepository;
    private final DailyMetricRepository dailyMetricRepository;
    private final BurnoutScoreRepository burnoutScoreRepository;
    private final MlModelRepository mlModelRepository;

    @Value("${app.scoring.threshold.green}")
    private double greenThreshold;

    @Value("${app.scoring.threshold.yellow}")
    private double yellowThreshold;

    @Transactional
    public void calculateScores(LocalDate targetDate) {
        log.info("Начинаем расчет выгорания для всех сотрудников на дату: {}", targetDate);

        MlModel activeModel = mlModelRepository.findByIsActiveTrue();
        if (activeModel == null) {
            log.error("Активная модель машинного обучения не найдена! Расчет невозможен.");
            return;
        }

        List<Employee> employees = employeeRepository.findAll();
        LocalDate monthAgo = targetDate.minusDays(30);

        List<DailyMetric> allHistoryMetrics = dailyMetricRepository.findAllByDateBetween(monthAgo, targetDate);
        Map<UUID, List<DailyMetric>> metricsByEmployee = allHistoryMetrics.stream()
                .collect(Collectors.groupingBy(m -> m.getEmployee().getId()));
        List<BurnoutScore> scoresToSave = new ArrayList<>();

        for (Employee employee : employees) {
            List<DailyMetric> history = metricsByEmployee.getOrDefault(employee.getId(), new ArrayList<>());

            DailyMetric targetMetric = history.stream()
                    .filter(m -> m.getDate().equals(targetDate))
                    .findFirst()
                    .orElse(null);

            // Изменен порог с 2 на 7 дней. Если оставить 2, то адекватно посчитать среднее отклонение не получится, модель будет выдавать случайный "шум"
            if (history.size() < 7 || targetMetric == null) {
                log.info("Недостаточно данных для оценки сотрудника {} (Холодный старт или нет метрик за {})", employee.getId(), targetDate);
                continue;
            }

            List<DailyMetric> pastHistory = history.stream()
                    .filter(m -> m.getDate().isBefore(targetDate))
                    .toList();

            if (pastHistory.isEmpty()) continue;

            List<DailyMetric> continuousHistory = padHistoryWithZeros(pastHistory, monthAgo, targetDate.minusDays(1), employee);

            double eeIndex = calculateEEIndex(targetMetric, continuousHistory);
            double dpIndex = calculateDPIndex(targetMetric, continuousHistory);
            double rpaIndex = calculateRPAIndex(targetMetric, continuousHistory);

            double zTotal = activeModel.getW0Bias() +
                    (activeModel.getW1Ee() * eeIndex) +
                    (activeModel.getW2Dp() * dpIndex) +
                    (activeModel.getW3Rpa() * rpaIndex);

            double riskProbability = MathUtils.sigmoid(zTotal);

            BurnoutScore score = new BurnoutScore();
            score.setEmployee(employee);
            score.setModel(activeModel);
            score.setTargetDate(targetDate);
            score.setCalculatedAt(LocalDateTime.now());
            score.setEeIndex(eeIndex);
            score.setDpIndex(dpIndex);
            score.setRpaIndex(rpaIndex);
            score.setRiskProbability(riskProbability);
            score.setStatusColor(determineStatusColor(riskProbability));

            scoresToSave.add(score);
        }

        burnoutScoreRepository.saveAll(scoresToSave);
        log.info("Расчет выгорания успешно завершен. Сохранено оценок: {}", scoresToSave.size());
    }

    private double calculateEEIndex(DailyMetric today, List<DailyMetric> history) {
        double totalWorkZ = getZScoreForMetric(today.getTotalWorkSeconds(), history.stream().map(DailyMetric::getTotalWorkSeconds).toList());
        double nightWorkZ = getZScoreForMetric(today.getNightWorkSeconds(), history.stream().map(DailyMetric::getNightWorkSeconds).toList());
        double weekendWorkZ = getZScoreForMetric(today.getWeekendWorkSeconds(), history.stream().map(DailyMetric::getWeekendWorkSeconds).toList());

        return (totalWorkZ + nightWorkZ + weekendWorkZ) / 3.0;
    }

    private double calculateDPIndex(DailyMetric today, List<DailyMetric> history) {
        double commitLenZ = getZScoreForMetric(today.getAvgCommitMsgLen(), history.stream().map(DailyMetric::getAvgCommitMsgLen).toList());
        double jiraCommentsZ = getZScoreForMetric(today.getJiraCommentsCount(), history.stream().map(DailyMetric::getJiraCommentsCount).toList());

        return ((-commitLenZ) + (-jiraCommentsZ)) / 2.0;
    }

    private double calculateRPAIndex(DailyMetric today, List<DailyMetric> history) {
        double prLeadTimeZ = getZScoreForMetric(today.getPrLeadTimeAvg(), history.stream().map(DailyMetric::getPrLeadTimeAvg).toList());
        double taskStagnationZ = getZScoreForMetric(today.getTaskStagnationSeconds(), history.stream().map(DailyMetric::getTaskStagnationSeconds).toList());
        double reopenRateZ = getZScoreForMetric(today.getReopenRate(), history.stream().map(DailyMetric::getReopenRate).toList());

        return (prLeadTimeZ + taskStagnationZ+ reopenRateZ) / 3.0;
    }

    private double getZScoreForMetric(int todayValue, List<Integer> historyValues) {
        double mean = com.lamart.burnout.burnoutpredictionsystem.util.MathUtils.calculateMean(historyValues);
        double stdDev = com.lamart.burnout.burnoutpredictionsystem.util.MathUtils.calculateStandardDeviation(historyValues, mean);
        return com.lamart.burnout.burnoutpredictionsystem.util.MathUtils.calculateZScore(todayValue, mean, stdDev);
    }

    private List<DailyMetric> padHistoryWithZeros(List<DailyMetric> actualHistory, LocalDate startDate, LocalDate endDate, Employee employee) {
        Map<LocalDate, DailyMetric> historyMap = actualHistory.stream()
                .collect(Collectors.toMap(DailyMetric::getDate, m -> m));

        List<DailyMetric> paddedHistory = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            if (historyMap.containsKey(d)) {
                paddedHistory.add(historyMap.get(d));
            } else {
                DailyMetric zeroMetric = new DailyMetric();
                zeroMetric.setEmployee(employee);
                zeroMetric.setDate(d);
                paddedHistory.add(zeroMetric);
            }
        }
        return paddedHistory;
    }

    private String determineStatusColor(double probability) {
        if (probability < greenThreshold) return "GREEN";
        if (probability < yellowThreshold) return "YELLOW";
        return "RED";
    }
}