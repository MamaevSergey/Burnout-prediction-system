package com.lamart.burnout.burnoutpredictionsystem.service.scoring;

import com.lamart.burnout.burnoutpredictionsystem.entity.BurnoutScore;
import com.lamart.burnout.burnoutpredictionsystem.entity.DailyMetric;
import com.lamart.burnout.burnoutpredictionsystem.entity.Employee;
import com.lamart.burnout.burnoutpredictionsystem.entity.MlModel;
import com.lamart.burnout.burnoutpredictionsystem.repository.BurnoutScoreRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.DailyMetricRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.EmployeeRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.MlModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoringEngineService {
    private final EmployeeRepository employeeRepository;
    private final DailyMetricRepository dailyMetricRepository;
    private final BurnoutScoreRepository burnoutScoreRepository;
    private final MlModelRepository mlModelRepository;

    @Transactional
    public void calculateScores(LocalDate targetDate) {
        log.info("Начинаем расчет выгорания для всех сотрудников на дату: {}", targetDate);

        MlModel activeModel = mlModelRepository.findByIsActiveTrue();
        if (activeModel == null) {
            log.error("Активная модель машинного обучения не найдена! Расчет невозможен.");
            return;
        }

        List<Employee> employees = employeeRepository.findAll();

        for (Employee employee : employees) {
            LocalDate monthAgo = targetDate.minusDays(30);

            List<DailyMetric> history = dailyMetricRepository.findAllByEmployeeIdAndDateAfter(employee.getId(), monthAgo);

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

            double eeIndex = calculateEEIndex(targetMetric, pastHistory);
            double dpIndex = calculateDPIndex(targetMetric, pastHistory);
            double rpaIndex = calculateRPAIndex(targetMetric, pastHistory);

            double zTotal = activeModel.getW0Bias() +
                    (activeModel.getW1Ee() * eeIndex) +
                    (activeModel.getW2Dp() * dpIndex) +
                    (activeModel.getW3Rpa() * rpaIndex);

            double riskProbability = com.lamart.burnout.burnoutpredictionsystem.util.MathUtils.sigmoid(zTotal);

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

            burnoutScoreRepository.save(score);
        }

        log.info("Расчет выгорания успешно завершен. Результаты сохранены в БД.");
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

    private String determineStatusColor(double probability) {
        if (probability < 0.40) return "GREEN";
        if (probability < 0.75) return "YELLOW";
        return "RED";
    }
}