package com.lamart.burnout.burnoutpredictionsystem.service.etl;

import com.lamart.burnout.burnoutpredictionsystem.entity.*;
import com.lamart.burnout.burnoutpredictionsystem.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricAggregationService {

    private final EmployeeRepository employeeRepository;
    private final GitCommitRepository gitCommitRepository;
    private final JiraTaskRepository jiraTaskRepository;
    private final DailyMetricRepository dailyMetricRepository;
    private final GitPullRequestRepository gitPullRequestRepository;
    private final JiraTaskCommentRepository commentRepository;
    private final JiraTaskChangelogRepository changelogRepository;
    private final SystemSettingsRepository settingsRepository;

    @Transactional
    public void aggregateMetricsForDate(LocalDate targetDate) {
        log.info("Начинаем агрегацию метрик за {}", targetDate);
        SystemSettings settings = settingsRepository.findById(1L).orElseThrow(() -> new IllegalStateException("Настройки системы не найдены"));

        List<String> doneStatuses = Arrays.stream(settings.getDoneStatuses().split(","))
                .map(String::trim).toList();
        List<String> boilerplateWords = Arrays.stream(settings.getBoilerplateWords().split(","))
                .map(String::trim).toList();

        String defaultTimezone = settings.getTimezone();
        int minCommitLength = settings.getMinCommitLength();
        long isolatedEventDurationSeconds = settings.getIsolatedEventDurationSeconds();

        List<Employee> employees = employeeRepository.findAll();
        if (employees.isEmpty()) return;

        List<UUID> empIds = employees.stream().map(Employee::getId).toList();

        LocalDateTime globalStartUtc = targetDate.minusDays(1).atStartOfDay();
        LocalDateTime globalEndUtc = targetDate.plusDays(1).atTime(23, 59, 59);

        Map<UUID, List<GitCommit>> commitsByEmp = gitCommitRepository.findByEmployeeIdInAndCommittedAtBetween(empIds, globalStartUtc, globalEndUtc)
                .stream().collect(Collectors.groupingBy(c -> c.getEmployee().getId()));
        Map<UUID, List<GitPullRequest>> prsByEmp = gitPullRequestRepository.findActivePRsForEmployees(empIds, globalStartUtc, globalEndUtc)
                .stream().collect(Collectors.groupingBy(pr -> pr.getEmployee().getId()));
        Map<UUID, List<JiraTask>> activeTasksByEmp = jiraTaskRepository.findByEmployeeIdInAndStatusNotIn(empIds, doneStatuses)
                .stream().collect(Collectors.groupingBy(t -> t.getEmployee().getId()));
        Map<UUID, List<JiraTaskComment>> commentsByEmp = commentRepository.findByEmployeeIdInAndCreatedAtBetween(empIds, globalStartUtc, globalEndUtc)
                .stream().collect(Collectors.groupingBy(c -> c.getEmployee().getId()));
        Map<UUID, List<JiraTaskChangelog>> changelogsByEmp = changelogRepository.findByTaskEmployeeIdInAndCreatedAtBetween(empIds, globalStartUtc, globalEndUtc)
                .stream().collect(Collectors.groupingBy(c -> c.getTask().getEmployee().getId()));
        Map<UUID, DailyMetric> existingMetricsByEmp = dailyMetricRepository.findByDate(targetDate)
                .stream().collect(Collectors.toMap(m -> m.getEmployee().getId(), m -> m));

        List<DailyMetric> metricsToSave = new ArrayList<>();

        for (Employee emp : employees) {
            UUID empId = emp.getId();
            String tz = emp.getTimezone() != null ? emp.getTimezone() : defaultTimezone;
            ZoneId employeeZone = ZoneId.of(tz);

            ZonedDateTime startOfDayLocal = targetDate.atStartOfDay(employeeZone);
            ZonedDateTime endOfDayLocal = targetDate.atTime(23, 59, 59).atZone(employeeZone);

            LocalDateTime startUtc = startOfDayLocal.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            LocalDateTime endUtc = endOfDayLocal.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

            List<GitCommit> commits = commitsByEmp.getOrDefault(empId, Collections.emptyList()).stream()
                    .filter(c -> !c.getCommittedAt().isBefore(startUtc) && !c.getCommittedAt().isAfter(endUtc)).toList();
            List<GitPullRequest> prs = prsByEmp.getOrDefault(empId, Collections.emptyList()).stream()
                    .filter(pr -> (pr.getCreatedAt() != null && !pr.getCreatedAt().isBefore(startUtc) && !pr.getCreatedAt().isAfter(endUtc)) ||
                            (pr.getMergedAt() != null && !pr.getMergedAt().isBefore(startUtc) && !pr.getMergedAt().isAfter(endUtc)))
                    .toList();
            List<JiraTask> tasks = activeTasksByEmp.getOrDefault(empId, Collections.emptyList());
            List<JiraTaskComment> comments = commentsByEmp.getOrDefault(empId, Collections.emptyList()).stream()
                    .filter(c -> !c.getCreatedAt().isBefore(startUtc) && !c.getCreatedAt().isAfter(endUtc)).toList();
            List<JiraTaskChangelog> changelogs = changelogsByEmp.getOrDefault(empId, Collections.emptyList()).stream()
                    .filter(c -> !c.getCreatedAt().isBefore(startUtc) && !c.getCreatedAt().isAfter(endUtc)).toList();

            List<LocalDateTime> allEventsUtc = buildTimeline(commits, prs, comments, changelogs, startUtc, endUtc);

            if (allEventsUtc.isEmpty() && tasks.isEmpty()) continue;

            DailyMetric metric = existingMetricsByEmp.getOrDefault(empId, new DailyMetric());
            if (metric.getId() == null) {
                metric.setEmployee(emp);
                metric.setDate(targetDate);
            }

            // Передаем timezone
            calculateEE(metric, allEventsUtc, employeeZone, isolatedEventDurationSeconds);
            calculateDP(metric, commits, comments, minCommitLength, boilerplateWords);
            calculateRPA(metric, prs, commits, tasks, changelogs, targetDate, doneStatuses);

            metricsToSave.add(metric);
        }

        dailyMetricRepository.saveAll(metricsToSave);
        log.info("Агрегация завершена. Сформировано/обновлено {} метрик за {}.", metricsToSave.size(), targetDate);
    }

    // Эмоциональное истощение (EE). Расчет activitySpanSeconds / nightEventsCount / weekendEventsCount
    private void calculateEE(DailyMetric metric, List<LocalDateTime> allEventsUtc, ZoneId employeeZone, long isolatedEventDurationSeconds) {
        long activitySpanSeconds = 0;

        if (!allEventsUtc.isEmpty()) {
            List<LocalDateTime> sortedEvents = allEventsUtc.stream().sorted().toList();
            // Логика сессий: Если между событиями больше 2 часов, это перерыв не считаем
            long maxSessionGapSeconds = 2 * 3600;
            LocalDateTime sessionStart = sortedEvents.getFirst();
            LocalDateTime lastEvent = sortedEvents.getFirst();

            for (int i = 1; i < sortedEvents.size(); i++) {
                LocalDateTime currentEvent = sortedEvents.get(i);
                long gap = ChronoUnit.SECONDS.between(lastEvent, currentEvent);

                if (gap > maxSessionGapSeconds) {
                    long sessionDuration = ChronoUnit.SECONDS.between(sessionStart, lastEvent);
                    activitySpanSeconds += (sessionDuration + isolatedEventDurationSeconds);
                    sessionStart = currentEvent;
                }
                lastEvent = currentEvent;
            }
            long lastSessionDuration = ChronoUnit.SECONDS.between(sessionStart, lastEvent);
            activitySpanSeconds += (lastSessionDuration + isolatedEventDurationSeconds);
        }
        metric.setActivitySpanSeconds(activitySpanSeconds);

        // Переводим события из UTC в местное время сотрудника для проверки "Ночи"
        int nightEvents = (int) allEventsUtc.stream()
                .map(utcTime -> utcTime.atZone(ZoneOffset.UTC).withZoneSameInstant(employeeZone))
                .filter(localTime -> localTime.getHour() >= 22 || localTime.getHour() < 6)
                .count();
        metric.setNightEventsCount(Math.min(nightEvents, 10)); // Если больше 10 ночных событий, то уже не важно, что дальше, факт переработки зафиксирован

        // Переводим события для проверки "Выходных"
        int weekendEvents = (int) allEventsUtc.stream()
                .map(utcTime -> utcTime.atZone(ZoneOffset.UTC).withZoneSameInstant(employeeZone))
                .filter(localTime -> localTime.getDayOfWeek() == DayOfWeek.SATURDAY || localTime.getDayOfWeek() == DayOfWeek.SUNDAY)
                .count();
        metric.setWeekendEventsCount(Math.min(weekendEvents, 10));
    }

    // Деперсонализация (DP).
    private void calculateDP(DailyMetric metric, List<GitCommit> commits, List<JiraTaskComment> comments, int minCommitLength, List<String> boilerplateWords) {
        double badCommitRatio = 0.0;
        if (!commits.isEmpty()) {
            long badCommitsCount = commits.stream()
                    .filter(c -> isBadCommit(c.getMessage(), minCommitLength, boilerplateWords))
                    .count();
            badCommitRatio = (double) badCommitsCount / commits.size();
        }
        metric.setBadCommitRatio(badCommitRatio);

        int totalEffort = 0;
        for (JiraTaskComment comment : comments) {
            int bodyLen = comment.getBodyLength() != null ? comment.getBodyLength() : 0;
            int attachments = comment.getAttachmentsCount() != null ? comment.getAttachmentsCount() : 0;

            int currentEffort = 5;
            if (bodyLen > 0) currentEffort += Math.min(bodyLen / 20, 50);
            if (attachments > 0) currentEffort += Math.min(attachments * 5, 20);

            totalEffort += currentEffort;
        }
        metric.setJiraEffortScore(totalEffort);
    }

    // Расчет Редукции достижений (RPA).
    private void calculateRPA(DailyMetric metric, List<GitPullRequest> prs, List<GitCommit> commits, List<JiraTask> tasks, List<JiraTaskChangelog> changelogs, LocalDate targetDate, List<String> doneStatuses) {
        double prLeadTimeAvgMinutes = 0.0;
        if (!prs.isEmpty()) {
            prLeadTimeAvgMinutes = prs.stream()
                    .filter(pr -> pr.getCreatedAt() != null && pr.getMergedAt() != null)
                    .mapToLong(pr -> {
                        long leadTime = ChronoUnit.MINUTES.between(pr.getCreatedAt(), pr.getMergedAt());
                        return Math.min(leadTime, 20160);
                    })
                    .average()
                    .orElse(0.0);
        }
        metric.setPrLeadTimeAvgMinutes(prLeadTimeAvgMinutes);

        int conflicts = (int) commits.stream()
                .filter(c -> c.getMessage() != null && (
                        c.getMessage().toLowerCase().contains("conflict") ||
                                c.getMessage().toLowerCase().contains("merge conflict")
                ))
                .count();
        metric.setMergeConflictsCount(Math.min(conflicts, 5));

        metric.setTaskStagnationSeconds(calculateTaskStagnation(tasks, targetDate));
        metric.setReopenRate(calculateReopenRate(changelogs, doneStatuses));
    }

    private List<LocalDateTime> buildTimeline(List<GitCommit> commits, List<GitPullRequest> prs,
                                              List<JiraTaskComment> comments, List<JiraTaskChangelog> changelogs,
                                              LocalDateTime startUtc, LocalDateTime endUtc) {
        List<LocalDateTime> allEvents = new ArrayList<>();
        commits.forEach(c -> allEvents.add(c.getCommittedAt()));

        for (GitPullRequest pr : prs) {
            if (pr.getCreatedAt() != null && !pr.getCreatedAt().isBefore(startUtc) && !pr.getCreatedAt().isAfter(endUtc)) {
                allEvents.add(pr.getCreatedAt());
            }
            if (pr.getMergedAt() != null && !pr.getMergedAt().isBefore(startUtc) && !pr.getMergedAt().isAfter(endUtc)) {
                allEvents.add(pr.getMergedAt());
            }
        }

        comments.forEach(c -> allEvents.add(c.getCreatedAt()));
        changelogs.forEach(c -> allEvents.add(c.getCreatedAt()));
        return allEvents;
    }

    private boolean isBadCommit(String message, int minCommitLength, List<String> boilerplateWords) {
        if (message == null || message.trim().isEmpty()) return true;
        String lowerMsg = message.trim().toLowerCase();
        if (lowerMsg.length() < minCommitLength) return true;

        for (String word : boilerplateWords) {
            String regex = "(?iU).*\\b" + Pattern.quote(word) + "\\b.*";
            if (lowerMsg.matches(regex)) {
                return true;
            }
        }
        return false;
    }

    // Считает и возвращает метрику taskStagnation в секундах
    private long calculateTaskStagnation(List<JiraTask> tasks, LocalDate targetDate) {
        long stagnation = 0;
        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime endOfDay = targetDate.atTime(23, 59, 59);

        LocalDateTime cutoffDate = startOfDay.minusDays(14);

        for (JiraTask task : tasks) {
            LocalDateTime lastUpdate = task.getUpdatedAt();
            if (lastUpdate == null) continue;
            if (!lastUpdate.isBefore(startOfDay)) continue;
            if (lastUpdate.isBefore(cutoffDate)) continue;

            long stagnantSeconds = ChronoUnit.SECONDS
                    .between(lastUpdate, endOfDay);
            stagnation += stagnantSeconds;
        }
        return Math.min(stagnation, 259200);
    }

    private double calculateReopenRate(List<JiraTaskChangelog> changelogs, List<String> doneStatuses) {
        long totalStatusChanges = changelogs.stream()
                .filter(c -> "status".equalsIgnoreCase(c.getFieldName()))
                .count();

        if (totalStatusChanges == 0) return 0.0;

        long reopens = changelogs.stream()
                .filter(c -> "status".equalsIgnoreCase(c.getFieldName()) &&
                        isDoneStatus(c.getFromString(), doneStatuses) &&
                        !isDoneStatus(c.getToString(), doneStatuses))
                .count();

        return (double) reopens / totalStatusChanges;
    }

    private boolean isDoneStatus(String status, List<String> doneStatuses) {
        if (status == null) return false;
        return doneStatuses.stream().anyMatch(s -> s.equalsIgnoreCase(status));
    }
}