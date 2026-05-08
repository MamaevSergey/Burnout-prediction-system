package com.lamart.burnout.burnoutpredictionsystem.service.scoring;

import com.lamart.burnout.burnoutpredictionsystem.entity.*;
import com.lamart.burnout.burnoutpredictionsystem.repository.*;
import com.lamart.burnout.burnoutpredictionsystem.util.MathUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoringEngineService {
    private final EmployeeRepository employeeRepository;
    private final DailyMetricRepository dailyMetricRepository;
    private final BurnoutScoreRepository burnoutScoreRepository;
    private final MlModelRepository mlModelRepository;
    private final SystemSettingsRepository settingsRepository;

    @Transactional
    public void calculateScores(LocalDate targetDate) {
        calculateScores(targetDate, false);
    }

    @Transactional
    public void calculateScoresForEmployee(LocalDate targetDate, UUID employeeId) {
        MlModel activeModel = mlModelRepository.findByIsActiveTrue().orElse(null);
        if (activeModel == null) return;

        SystemSettings settings = settingsRepository.findById(1L).orElseThrow(() -> new IllegalStateException("Настройки системы не найдены"));
        double alpha = settings.getScoringAlpha();
        double greenThreshold = settings.getGreenThreshold();
        double yellowThreshold = settings.getYellowThreshold();

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Сотрудник не найден"));

        LocalDate startDate = targetDate.minusDays(30);

        List<DailyMetric> actualHistory = dailyMetricRepository.findAllByEmployeeIdAndDateBetween(
                employee.getId(), startDate, targetDate);

        if (actualHistory.size() < 5) return;

        LocalDate firstActivityDate = actualHistory.stream()
                .map(DailyMetric::getDate)
                .min(LocalDate::compareTo)
                .orElse(startDate);

        LocalDate effectiveStartDate = firstActivityDate.isAfter(startDate) ? firstActivityDate : startDate;

        List<DailyMetric> paddedHistory = padHistoryWithZeros(actualHistory, effectiveStartDate, targetDate, employee);
        DailyMetric todayMetric = paddedHistory.getLast();
        List<DailyMetric> pastHistory = paddedHistory.subList(0, paddedHistory.size() - 1);

        boolean isWeekend = todayMetric.getDate().getDayOfWeek() == DayOfWeek.SATURDAY ||
                todayMetric.getDate().getDayOfWeek() == DayOfWeek.SUNDAY;

        List<DailyMetric> pastWeekendHistory = pastHistory.stream()
                .filter(m -> m.getDate().getDayOfWeek() == DayOfWeek.SATURDAY ||
                        m.getDate().getDayOfWeek() == DayOfWeek.SUNDAY)
                .toList();

        List<DailyMetric> pastWeekdayHistory = pastHistory.stream()
                .filter(m -> m.getDate().getDayOfWeek() != DayOfWeek.SATURDAY &&
                        m.getDate().getDayOfWeek() != DayOfWeek.SUNDAY)
                .toList();

        double zActivitySpan = isWeekend ? 0.0 :
                getZScore(todayMetric.getActivitySpanSeconds(), pastWeekdayHistory, m -> (double) m.getActivitySpanSeconds(), true, 3600.0);
        double zNightEvents = (isWeekend && todayMetric.getNightEventsCount() == 0) ? 0.0 :
                getZScore(todayMetric.getNightEventsCount(), pastHistory, m -> (double) m.getNightEventsCount(), false, 1.0);
        double zWeekendEvents = isWeekend ?
                getZScore(todayMetric.getWeekendEventsCount(), pastWeekendHistory, m -> (double) m.getWeekendEventsCount(), false, 1.0) : 0.0;

        double rawEeIndex = zActivitySpan + zNightEvents + zWeekendEvents;

        double hoursWorkedToday = todayMetric.getActivitySpanSeconds() / 3600.0;
        if (hoursWorkedToday > 12.0) rawEeIndex += 1.0;
        else if (hoursWorkedToday > 10.0) rawEeIndex += 0.5;

        if (todayMetric.getNightEventsCount() > 0 && hoursWorkedToday > 9.0) rawEeIndex += 0.5;

        long weekendDaysWorked = paddedHistory.stream().filter(m -> m.getWeekendEventsCount() > 0).count();
        if (weekendDaysWorked > 6) rawEeIndex += 0.5;
        else if (weekendDaysWorked > 3) rawEeIndex += 0.25;

        double zBadCommit = (isWeekend && todayMetric.getBadCommitRatio() == 0.0) ? 0.0 :
                getZScore(todayMetric.getBadCommitRatio(), pastHistory, DailyMetric::getBadCommitRatio, false, 0.10);
        double zJiraEffort = (isWeekend && todayMetric.getJiraEffortScore() == 0) ? 0.0 :
                getZScore(todayMetric.getJiraEffortScore(), pastHistory, m -> (double) m.getJiraEffortScore(), true, 3.0);

        double rawDpIndex = zBadCommit + (-zJiraEffort);

        double zPrLeadTime = (todayMetric.getPrLeadTimeAvgMinutes() == 0.0) ? 0.0 :
                getZScore(todayMetric.getPrLeadTimeAvgMinutes(), pastHistory, DailyMetric::getPrLeadTimeAvgMinutes, true, 60.0);

        double zTaskStag = (isWeekend && todayMetric.getTaskStagnationSeconds() == 0) ? 0.0 :
                getZScore(todayMetric.getTaskStagnationSeconds(), pastHistory, m -> (double) m.getTaskStagnationSeconds(), false, 86400.0);

        double zReopen = (isWeekend && todayMetric.getReopenRate() == 0.0) ? 0.0 :
                getZScore(todayMetric.getReopenRate(), pastHistory, DailyMetric::getReopenRate, false, 0.10);

        double zMergeConf = (isWeekend && todayMetric.getMergeConflictsCount() == 0) ? 0.0 :
                getZScore(todayMetric.getMergeConflictsCount(), pastHistory, m -> (double) m.getMergeConflictsCount(), false, 1.0);
        double rawRpaIndex = zPrLeadTime + zTaskStag + zReopen + zMergeConf;

        double eeIndex = rawEeIndex;
        double dpIndex = rawDpIndex;
        double rpaIndex = rawRpaIndex;

        var lastHistoricalScoreOpt = burnoutScoreRepository.findTopByEmployeeIdAndTargetDateBeforeOrderByTargetDateDesc(employee.getId(), targetDate);

        if (lastHistoricalScoreOpt.isPresent()) {
            BurnoutScore lastScore = lastHistoricalScoreOpt.get();
            eeIndex = (alpha * rawEeIndex) + ((1.0 - alpha) * lastScore.getEeIndex());
            dpIndex = (alpha * rawDpIndex) + ((1.0 - alpha) * lastScore.getDpIndex());
            rpaIndex = (alpha * rawRpaIndex) + ((1.0 - alpha) * lastScore.getRpaIndex());
        }

        double zTotal = activeModel.getW0Bias() + (activeModel.getW1Ee() * eeIndex) + (activeModel.getW2Dp() * dpIndex) + (activeModel.getW3Rpa() * rpaIndex);
        double probability = 1.0 / (1.0 + Math.exp(-zTotal));

        BurnoutScore score = burnoutScoreRepository
                .findTopByEmployeeIdAndTargetDate(employee.getId(), targetDate)
                .orElseGet(BurnoutScore::new);

        score.setEmployee(employee);
        score.setModel(activeModel);
        score.setCalculatedAt(LocalDateTime.now());
        score.setTargetDate(targetDate);
        score.setEeIndex(eeIndex);
        score.setDpIndex(dpIndex);
        score.setRpaIndex(rpaIndex);
        score.setRiskProbability(probability);
        score.setStatusColor(determineStatusColor(probability, greenThreshold, yellowThreshold));

        burnoutScoreRepository.save(score);
    }

    @Transactional
    public void calculateScores(LocalDate targetDate, boolean isBackfill) {
        if (!isBackfill) {
            LocalDate yesterday = targetDate.minusDays(1);
            if (!burnoutScoreRepository.existsByTargetDate(yesterday)) {
                log.info("Нет данных за {}. Запускаем Backfill.", yesterday);
                calculateScores(yesterday, true);
            }
        }

        MlModel activeModel = mlModelRepository.findByIsActiveTrue().orElse(null);
        if (activeModel == null) {
            log.error("Активная модель машинного обучения не найдена! Расчет невозможен.");
            return;
        }

        SystemSettings settings = settingsRepository.findById(1L).orElseThrow(() -> new IllegalStateException("Настройки системы не найдены"));
        double alpha = settings.getScoringAlpha();
        double greenThreshold = settings.getGreenThreshold();
        double yellowThreshold = settings.getYellowThreshold();

        List<Employee> employees = employeeRepository.findAll();
        LocalDate startDate = targetDate.minusDays(30);

        List<DailyMetric> allMetrics = dailyMetricRepository.findAllByDateBetween(startDate, targetDate);
        Map<UUID, List<DailyMetric>> metricsByEmp = allMetrics.stream()
                .collect(Collectors.groupingBy(m -> m.getEmployee().getId()));

        List<BurnoutScore> scoresToSave = new ArrayList<>();
        int savedCount = 0;

        for (Employee emp : employees) {
            List<DailyMetric> actualHistory = metricsByEmp.getOrDefault(emp.getId(), new ArrayList<>());
            actualHistory.sort(Comparator.comparing(DailyMetric::getDate));

            if (actualHistory.size() < 5) {
                log.info("Недостаточно данных для оценки сотрудника {} (всего {} дней). Пропускаем ML-скоринг.", emp.getId(), actualHistory.size());
                continue;
            }

            LocalDate firstActivityDate = actualHistory.stream()
                    .map(DailyMetric::getDate)
                    .min(LocalDate::compareTo)
                    .orElse(startDate);

            LocalDate effectiveStartDate = firstActivityDate.isAfter(startDate) ? firstActivityDate : startDate;

            // Логика заполнения нулями
            List<DailyMetric> paddedHistory = padHistoryWithZeros(actualHistory, effectiveStartDate, targetDate, emp);
            DailyMetric todayMetric = paddedHistory.getLast();
            List<DailyMetric> pastHistory = paddedHistory.subList(0, paddedHistory.size() - 1);

            boolean isWeekend = todayMetric.getDate().getDayOfWeek() == DayOfWeek.SATURDAY ||
                    todayMetric.getDate().getDayOfWeek() == DayOfWeek.SUNDAY;

            List<DailyMetric> pastWeekendHistory = pastHistory.stream()
                    .filter(m -> m.getDate().getDayOfWeek() == DayOfWeek.SATURDAY ||
                            m.getDate().getDayOfWeek() == DayOfWeek.SUNDAY)
                    .toList();

            List<DailyMetric> pastWeekdayHistory = pastHistory.stream()
                    .filter(m -> m.getDate().getDayOfWeek() != DayOfWeek.SATURDAY &&
                            m.getDate().getDayOfWeek() != DayOfWeek.SUNDAY)
                    .toList();

            // EE Index
            double zActivitySpan = isWeekend ? 0.0 :
                    getZScore(todayMetric.getActivitySpanSeconds(), pastWeekdayHistory, m -> (double) m.getActivitySpanSeconds(), true, 3600.0);
            double zNightEvents = (isWeekend && todayMetric.getNightEventsCount() == 0) ? 0.0 :
                    getZScore(todayMetric.getNightEventsCount(), pastHistory, m -> (double) m.getNightEventsCount(), false, 1.0);
            double zWeekendEvents = isWeekend ?
                    getZScore(todayMetric.getWeekendEventsCount(), pastWeekendHistory, m -> (double) m.getWeekendEventsCount(), false, 1.0) : 0.0;

            double rawEeIndex = zActivitySpan + zNightEvents + zWeekendEvents;

            double hoursWorkedToday = todayMetric.getActivitySpanSeconds() / 3600.0;
            if (hoursWorkedToday > 12.0) {
                log.info("Для сотрудника: {} был начислен штраф 1.0 за > 12 часов работы. За дату {}", emp.getId(), todayMetric.getDate());
                rawEeIndex += 1.0;
            } else if (hoursWorkedToday > 10.0) {
                log.info("Для сотрудника: {} был начислен штраф 0.5 за > 10 часов работы. За дату {}", emp.getId(), todayMetric.getDate());
                rawEeIndex += 0.5;
            }

            if (todayMetric.getNightEventsCount() > 0 && hoursWorkedToday > 9.0) {
                rawEeIndex += 0.5;
                log.info("Для сотрудника: {} был начислен штраф 0.5 за ночную работу. За дату {}", emp.getId(), todayMetric.getDate());
            }

            long weekendDaysWorked = paddedHistory.stream()
                    .filter(m -> m.getWeekendEventsCount() > 0)
                    .count();

            if (weekendDaysWorked > 6) {
                log.info("Для сотрудника: {} был начислен штраф 0.5 за > 4 дней в выходные. За дату {}", emp.getId(), todayMetric.getDate());
                rawEeIndex += 0.5;
            } else if (weekendDaysWorked > 3) {
                log.info("Для сотрудника: {} был начислен штраф 0.5 за > 2 дней в выходные. За дату {}", emp.getId(), todayMetric.getDate());
                rawEeIndex += 0.25;
            }

            // DP Index
            double zBadCommit = (isWeekend && todayMetric.getBadCommitRatio() == 0.0) ? 0.0 :
                    getZScore(todayMetric.getBadCommitRatio(), pastHistory, DailyMetric::getBadCommitRatio, false, 0.10);
            double zJiraEffort = (isWeekend && todayMetric.getJiraEffortScore() == 0) ? 0.0 :
                    getZScore(todayMetric.getJiraEffortScore(), pastHistory, m -> (double) m.getJiraEffortScore(), true, 3.0);

            double rawDpIndex = zBadCommit + (-zJiraEffort);

            // RPA Index
            double zPrLeadTime = (todayMetric.getPrLeadTimeAvgMinutes() == 0.0) ? 0.0 :
                    getZScore(todayMetric.getPrLeadTimeAvgMinutes(), pastHistory, DailyMetric::getPrLeadTimeAvgMinutes, true, 60.0);
            double zTaskStag = (isWeekend && todayMetric.getTaskStagnationSeconds() == 0) ? 0.0 :
                    getZScore(todayMetric.getTaskStagnationSeconds(), pastHistory, m -> (double) m.getTaskStagnationSeconds(), false, 86400.0);
            double zReopen = (isWeekend && todayMetric.getReopenRate() == 0.0) ? 0.0 :
                    getZScore(todayMetric.getReopenRate(), pastHistory, DailyMetric::getReopenRate, false, 0.10);
            double zMergeConf = (isWeekend && todayMetric.getMergeConflictsCount() == 0) ? 0.0 :
                    getZScore(todayMetric.getMergeConflictsCount(), pastHistory, m -> (double) m.getMergeConflictsCount(), false, 1.0);

            double rawRpaIndex = zPrLeadTime + zTaskStag + zReopen + zMergeConf;

            double eeIndex = rawEeIndex;
            double dpIndex = rawDpIndex;
            double rpaIndex = rawRpaIndex;

            var lastHistoricalScoreOpt = burnoutScoreRepository.findTopByEmployeeIdAndTargetDateBeforeOrderByTargetDateDesc(emp.getId(), targetDate);

            if (lastHistoricalScoreOpt.isPresent()) {
                BurnoutScore lastScore = lastHistoricalScoreOpt.get();
                eeIndex = (alpha * rawEeIndex) + ((1.0 - alpha) * lastScore.getEeIndex());
                dpIndex = (alpha * rawDpIndex) + ((1.0 - alpha) * lastScore.getDpIndex());
                rpaIndex = (alpha * rawRpaIndex) + ((1.0 - alpha) * lastScore.getRpaIndex());
            }

            double zTotal = activeModel.getW0Bias() +
                    (activeModel.getW1Ee() * eeIndex) +
                    (activeModel.getW2Dp() * dpIndex) +
                    (activeModel.getW3Rpa() * rpaIndex);

            double probability = 1.0 / (1.0 + Math.exp(-zTotal));

            BurnoutScore score = burnoutScoreRepository
                    .findTopByEmployeeIdAndTargetDate(emp.getId(), targetDate)
                    .orElseGet(BurnoutScore::new);

            score.setEmployee(emp);
            score.setModel(activeModel);
            score.setCalculatedAt(LocalDateTime.now());
            score.setTargetDate(targetDate);
            score.setEeIndex(eeIndex);
            score.setDpIndex(dpIndex);
            score.setRpaIndex(rpaIndex);
            score.setRiskProbability(probability);
            score.setStatusColor(determineStatusColor(probability, greenThreshold, yellowThreshold));

            scoresToSave.add(score);
            savedCount++;
        }
        burnoutScoreRepository.saveAll(scoresToSave);

        if (!isBackfill) {
            log.info("Расчет выгорания за {} успешно завершен. Сохранено оценок: {}", targetDate, savedCount);
        }
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

                zeroMetric.setActivitySpanSeconds(0L);
                zeroMetric.setNightEventsCount(0);
                zeroMetric.setWeekendEventsCount(0);
                zeroMetric.setBadCommitRatio(0.0);
                zeroMetric.setJiraEffortScore(0);
                zeroMetric.setPrLeadTimeAvgMinutes(0.0);
                zeroMetric.setTaskStagnationSeconds(0L);
                zeroMetric.setReopenRate(0.0);
                zeroMetric.setMergeConflictsCount(0);

                paddedHistory.add(zeroMetric);
            }
        }
        return paddedHistory;
    }

    private double getZScore(double todayValue,
                             List<DailyMetric> history,
                             Function<DailyMetric, Double> mapper,
                             boolean filterZeros,
                             double minStdDev) {
        List<Double> values = history.stream().map(mapper).collect(Collectors.toList());
        if (filterZeros) {
            values = values.stream().filter(v -> v > 0).toList();
        }
        if (values.isEmpty()) return 0.0;

        double mean = MathUtils.calculateMean(values);
        double stdDev = MathUtils.calculateStandardDeviation(values, mean);

        double effectiveStdDev = Math.max(stdDev, minStdDev);
        double rawZ = MathUtils.calculateZScore(todayValue, mean, effectiveStdDev);
        return Math.max(-2.0, Math.min(3.0, rawZ));
    }

    private String determineStatusColor(double probability, double greenThreshold, double yellowThreshold) {
        if (probability < greenThreshold) return "GREEN";
        if (probability < yellowThreshold) return "YELLOW";
        return "RED";
    }
}