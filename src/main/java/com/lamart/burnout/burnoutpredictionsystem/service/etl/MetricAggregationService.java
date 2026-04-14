package com.lamart.burnout.burnoutpredictionsystem.service.etl;

import com.lamart.burnout.burnoutpredictionsystem.entity.*;
import com.lamart.burnout.burnoutpredictionsystem.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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

    @Value("${app.metrics.status.done}")
    private List<String> doneStatuses;

    @Value("${app.metrics.session.max-gap-seconds}")
    private long maxGapSeconds;

    @Value("${app.metrics.session.min-duration-seconds}")
    private long minSessionSeconds;

    @Value("${app.metrics.session.max-daily-seconds}")
    private long maxDailySeconds;

    @Transactional
    public void aggregateMetricForDate(LocalDate targetDate) {
        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime endOfDay = targetDate.atTime(LocalTime.MAX);

        log.info("Начинаем агрегацию метрик за {}", targetDate);

        List<Employee> employees = employeeRepository.findAll();

        List<GitCommit> allCommits = gitCommitRepository.findAllByCommittedAtBetween(startOfDay, endOfDay);
        List<JiraTask> allTasks = jiraTaskRepository.findAllByUpdatedAtBetween(startOfDay, endOfDay);
        List<GitPullRequest> allPrs = gitPullRequestRepository.findAllByMergedAtBetween(startOfDay, endOfDay);
        List<JiraTaskComment> allComments = commentRepository.findAllByCreatedAtBetween(startOfDay, endOfDay);
        List<JiraTaskChangelog> allReopens = changelogRepository.findReopensBetween(startOfDay, endOfDay);
        List<DailyMetric> existingMetrics = dailyMetricRepository.findAllByDate(targetDate);

        var commitsByEmp = allCommits.stream().collect(Collectors.groupingBy(c -> c.getEmployee().getId()));
        var tasksByEmp = allTasks.stream().collect(Collectors.groupingBy(t -> t.getEmployee().getId()));
        var prsByEmp = allPrs.stream().collect(Collectors.groupingBy(p -> p.getEmployee().getId()));
        var commentsByEmp = allComments.stream().collect(Collectors.groupingBy(c -> c.getEmployee().getId()));
        var reopensByEmp = allReopens.stream().collect(Collectors.groupingBy(c -> c.getTask().getEmployee().getId()));
        var metricsMap = existingMetrics.stream().collect(Collectors.toMap(m -> m.getEmployee().getId(), m -> m));

        List<DailyMetric> metricsToSave = new ArrayList<>();

        for (Employee emp : employees) {
            UUID empId = emp.getId();

            List<GitCommit> commits = commitsByEmp.getOrDefault(empId, Collections.emptyList());
            List<JiraTask> tasks = tasksByEmp.getOrDefault(empId, Collections.emptyList());
            List<GitPullRequest> prs = prsByEmp.getOrDefault(empId, Collections.emptyList());
            List<JiraTaskComment> comments = commentsByEmp.getOrDefault(empId, Collections.emptyList());

            if (commits.isEmpty() && tasks.isEmpty() && prs.isEmpty() && comments.isEmpty()) continue;

            DailyMetric metric = metricsMap.getOrDefault(empId, new DailyMetric());
            if (metric.getId() == null) {
                metric.setEmployee(emp);
                metric.setDate(targetDate);
            }

            int totalMsgLen = commits.stream().mapToInt(GitCommit::getMessageLength).sum();
            metric.setAvgCommitMsgLen(commits.isEmpty() ? 0 : totalMsgLen / commits.size());
            metric.setJiraCommentsCount(comments.size());

            List<LocalDateTime> allEvents = new ArrayList<>();
            commits.forEach(c -> allEvents.add(c.getCommittedAt()));
            tasks.forEach(t -> allEvents.add(t.getUpdatedAt()));
            prs.forEach(p -> allEvents.add(p.getMergedAt()));
            comments.forEach(c -> allEvents.add(c.getCreatedAt()));

            int totalWorkSeconds = calculateActiveSeconds(allEvents, startOfDay, endOfDay);
            if (totalWorkSeconds > maxDailySeconds) totalWorkSeconds = (int) maxDailySeconds;
            metric.setTotalWorkSeconds(totalWorkSeconds);

            boolean isWeekend = targetDate.getDayOfWeek().getValue() >= 6;
            metric.setWeekendWorkSeconds(isWeekend ? totalWorkSeconds : 0);

            int nightWorkSeconds = calculateActiveSeconds(allEvents, targetDate.atTime(0, 0), targetDate.atTime(7, 0)) +
                    calculateActiveSeconds(allEvents, targetDate.atTime(22, 0), targetDate.atTime(23, 59, 59));
            metric.setNightWorkSeconds(nightWorkSeconds);

            double avgPrLead = prs.stream().mapToInt(GitPullRequest::getLeadTimeMinutes).average().orElse(0.0);
            metric.setPrLeadTimeAvg((int) avgPrLead);

            long stagnationSecs = tasks.stream()
                    .filter(t -> t.getStatus() != null && !isDoneStatus(t.getStatus()))
                    .mapToLong(t -> {
                        if (t.getCreatedAt() != null && t.getCreatedAt().isBefore(endOfDay)) {
                            return ChronoUnit.SECONDS.between(t.getCreatedAt(), endOfDay);
                        }
                        return 0L;
                    })
                    .sum();
            metric.setTaskStagnationSeconds((int) stagnationSecs);

            metric.setReopenRate(reopensByEmp.getOrDefault(empId, Collections.emptyList()).size());

            metricsToSave.add(metric);
        }
        dailyMetricRepository.saveAll(metricsToSave);
        log.info("Агрегация завершена. Сформировано/обновлено {} метрик за {}.", metricsToSave.size(), targetDate);
    }

    private boolean isDoneStatus(String status) {
        if (status == null) return false;
        return doneStatuses.stream().anyMatch(s -> s.equalsIgnoreCase(status));
    }

    private int calculateActiveSeconds(List<LocalDateTime> events, LocalDateTime windowStart, LocalDateTime windowEnd) {
        List<LocalDateTime> filteredEvents = events.stream()
                .filter(t -> !t.isBefore(windowStart) && t.isBefore(windowEnd))
                .sorted()
                .toList();

        if (filteredEvents.isEmpty()) return 0;

        long totalSeconds = 0;
        LocalDateTime sessionStart = filteredEvents.getFirst();
        LocalDateTime sessionEnd = sessionStart;

        for (int i = 1; i < filteredEvents.size(); i++) {
            LocalDateTime current = filteredEvents.get(i);
            long gap = ChronoUnit.SECONDS.between(sessionEnd, current);

            if (gap > maxGapSeconds) {
                long sessionDuration = ChronoUnit.SECONDS.between(sessionStart, sessionEnd);
                totalSeconds += Math.max(sessionDuration, minSessionSeconds);
                sessionStart = current;
            }
            sessionEnd = current;
        }
        long lastSessionDuration = ChronoUnit.SECONDS.between(sessionStart, sessionEnd);
        totalSeconds += Math.max(lastSessionDuration, minSessionSeconds);

        return (int) totalSeconds;
    }
}
