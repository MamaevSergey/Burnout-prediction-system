package com.lamart.burnout.burnoutpredictionsystem.service.etl;

import com.lamart.burnout.burnoutpredictionsystem.entity.*;
import com.lamart.burnout.burnoutpredictionsystem.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    @Transactional
    public void aggregateMetricForDate(LocalDate targetDate) {
        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime endOfDay = targetDate.atTime(LocalTime.MAX);

        log.info("Начинаем агрегацию метрик за {}", targetDate);

        List<Employee> employees = employeeRepository.findAll();
        int metricsCreated = 0;

        for (Employee emp : employees) {
            List<GitCommit> commits = gitCommitRepository.findAllByEmployeeIdAndCommittedAtBetween(emp.getId(), startOfDay, endOfDay);
            List<JiraTask> tasks = jiraTaskRepository.findAllByEmployeeIdAndUpdatedAtBetween(emp.getId(), startOfDay, endOfDay);
            List<GitPullRequest> prs = gitPullRequestRepository.findAllByEmployeeIdAndMergedAtBetween(emp.getId(), startOfDay, endOfDay);

            // Если активности не было вообще, пропускаем
            if (commits.isEmpty() && tasks.isEmpty() && prs.isEmpty()) continue;

            DailyMetric metric = dailyMetricRepository.findByEmployeeIdAndDate(emp.getId(), targetDate)
                    .orElseGet(() -> {
                        DailyMetric newMetric = new DailyMetric();
                        newMetric.setEmployee(emp);
                        newMetric.setDate(targetDate);
                        return newMetric;
                    });

            int totalMsgLen = commits.stream().mapToInt(GitCommit::getMessageLength).sum();
            metric.setAvgCommitMsgLen(commits.isEmpty() ? 0 : totalMsgLen / commits.size());

            int commentsCount = commentRepository.countByEmployeeIdAndCreatedAtBetween(emp.getId(), startOfDay, endOfDay);
            metric.setJiraCommentsCount(commentsCount);

            LocalDateTime firstEvent = getFirstEvent(commits, tasks, prs, endOfDay);
            LocalDateTime lastEvent = getLastEvent(commits, tasks, prs, startOfDay);

            int totalWorkSeconds = 0;
            if (!firstEvent.isAfter(lastEvent)) {
                long workSeconds = ChronoUnit.SECONDS.between(firstEvent, lastEvent);
                totalWorkSeconds = workSeconds > (12 * 3600) ? (12 * 3600) : (int) workSeconds;
                if (totalWorkSeconds < 3600) totalWorkSeconds = 3600;
            }
            metric.setTotalWorkSeconds(totalWorkSeconds);

            boolean isWeekend = targetDate.getDayOfWeek().getValue() >= 6;
            metric.setWeekendWorkSeconds(isWeekend ? totalWorkSeconds : 0);

            int nightWorkSeconds = calculateWorkSpanInWindow(commits, tasks, prs,
                    targetDate.atTime(0, 0), targetDate.atTime(7, 0)) +
                    calculateWorkSpanInWindow(commits, tasks, prs,
                            targetDate.atTime(22, 0), targetDate.atTime(23, 59, 59));
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

            int reopenCount = changelogRepository.countReopensByEmployee(emp.getId(), startOfDay, endOfDay);
            metric.setReopenRate(reopenCount);

            dailyMetricRepository.save(metric);
            metricsCreated++;
        }

        log.info("Агрегация завершена. Сформировано/обновлено {} метрик за вчерашний день.", metricsCreated);
    }

    private boolean isDoneStatus(String status) {
        return status.equalsIgnoreCase("Done") || status.equalsIgnoreCase("Готово") || status.equalsIgnoreCase("Closed");
    }

    private int calculateWorkSpanInWindow(List<GitCommit> commits, List<JiraTask> tasks, List<GitPullRequest> prs,
                                          LocalDateTime windowStart, LocalDateTime windowEnd) {
        List<LocalDateTime> events = new ArrayList<>();

        commits.stream().map(GitCommit::getCommittedAt)
                .filter(t -> !t.isBefore(windowStart) && t.isBefore(windowEnd)).forEach(events::add);
        tasks.stream().map(JiraTask::getUpdatedAt)
                .filter(t -> !t.isBefore(windowStart) && t.isBefore(windowEnd)).forEach(events::add);
        prs.stream().map(GitPullRequest::getMergedAt)
                .filter(t -> !t.isBefore(windowStart) && t.isBefore(windowEnd)).forEach(events::add);

        if (events.isEmpty()) return 0;
        if (events.size() == 1) return 1800;

        LocalDateTime first = Collections.min(events);
        LocalDateTime last = Collections.max(events);

        long seconds = ChronoUnit.SECONDS.between(first, last);
        return seconds < 1800 ? 1800 : (int) seconds;
    }

    private LocalDateTime getFirstEvent(List<GitCommit> commits, List<JiraTask> tasks, List<GitPullRequest> prs, LocalDateTime fallback) {
        LocalDateTime first = fallback;
        for (GitCommit c : commits) if (c.getCommittedAt().isBefore(first)) first = c.getCommittedAt();
        for (JiraTask t : tasks) if (t.getUpdatedAt().isBefore(first)) first = t.getUpdatedAt();
        for (GitPullRequest p : prs) if (p.getMergedAt().isBefore(first)) first = p.getMergedAt();
        return first;
    }

    private LocalDateTime getLastEvent(List<GitCommit> commits, List<JiraTask> tasks, List<GitPullRequest> prs, LocalDateTime fallback) {
        LocalDateTime last = fallback;
        for (GitCommit c : commits) if (c.getCommittedAt().isAfter(last)) last = c.getCommittedAt();
        for (JiraTask t : tasks) if (t.getUpdatedAt().isAfter(last)) last = t.getUpdatedAt();
        for (GitPullRequest p : prs) if (p.getMergedAt().isAfter(last)) last = p.getMergedAt();
        return last;
    }
}
