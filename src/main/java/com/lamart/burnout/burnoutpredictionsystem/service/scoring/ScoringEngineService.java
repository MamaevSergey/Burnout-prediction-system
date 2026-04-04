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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
    public void calculateScoresForToday() {
        LocalDate today = LocalDate.now();
        log.info("Начинаем расчет выгорания для всех сотрудников на дату: {}", today);

        MlModel activeModel = mlModelRepository.findByIsActiveTrue();
        if (activeModel == null) {
            log.error("Активная модель машинного обучения не найдена! Расчет невозможен.");
            return;
        }

        List<Employee> employees = employeeRepository.findAll();

        for (Employee employee : employees) {
            LocalDate monthAgo = today.minusDays(30);

            List<DailyMetric> history = dailyMetricRepository.findAllByEmployeeIdAndDateAfter(employee.getId(), monthAgo);

            if (history.size() < 2) {
                log.info("Недостаточно данных для анализа...");
                continue;
            }

            DailyMetric todayMetric = history.getLast();
            List<DailyMetric> pastHistory = history.subList(0, history.size() - 1);

            double eeIndex = calculateEEIndex(todayMetric, pastHistory);
            double dpIndex = calculateDPIndex(todayMetric, pastHistory);
            double rpaIndex = calculateRPAIndex(todayMetric, pastHistory);

            double zTotal = activeModel.getW0Bias() +
                    (activeModel.getW1Ee() * eeIndex) +
                    (activeModel.getW2Dp() * dpIndex) +
                    (activeModel.getW3Rpa() * rpaIndex);

            double riskProbability = com.lamart.burnout.burnoutpredictionsystem.util.MathUtils.sigmoid(zTotal);

            String statusColor = determineStatusColor(riskProbability);

            BurnoutScore score = new BurnoutScore();
            score.setEmployee(employee);
            score.setModel(activeModel);
            score.setCalculatedAt(LocalDateTime.now());
            score.setEeIndex(eeIndex);
            score.setDpIndex(dpIndex);
            score.setRpaIndex(rpaIndex);
            score.setRiskProbability(riskProbability);
            score.setStatusColor(statusColor);

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