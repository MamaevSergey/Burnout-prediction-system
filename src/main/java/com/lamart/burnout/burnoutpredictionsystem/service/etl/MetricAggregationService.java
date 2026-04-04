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

    @Transactional
    public void aggregateForYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime startOfDay = yesterday.atStartOfDay();
        LocalDateTime endOfDay = yesterday.atTime(LocalTime.MAX);

        log.info("Начинаем агрегацию метрик за {}", yesterday);

        List<Employee> employees = employeeRepository.findAll();
        int metricsCreated = 0;

        for (Employee emp : employees) {
            List<GitCommit> commits = gitCommitRepository.findAllByEmployeeIdAndCommittedAtBetween(emp.getId(), startOfDay, endOfDay);
            List<JiraTask> tasks = jiraTaskRepository.findAllByEmployeeIdAndUpdatedAtBetween(emp.getId(), startOfDay, endOfDay);
            List<GitPullRequest> prs = gitPullRequestRepository.findAllByEmployeeIdAndMergedAtBetween(emp.getId(), startOfDay, endOfDay);

            if (commits.isEmpty() && tasks.isEmpty() && prs.isEmpty()) continue;

            DailyMetric metric = dailyMetricRepository.findByEmployeeIdAndDate(emp.getId(), yesterday)
                    .orElseGet(() -> {
                        DailyMetric newMetric = new DailyMetric();
                        newMetric.setEmployee(emp);
                        newMetric.setDate(yesterday);
                        return newMetric;
                    });
            
            int totalMsgLen = commits.stream().mapToInt(GitCommit::getMessageLength).sum();
            metric.setAvgCommitMsgLen(commits.isEmpty() ? 0 : totalMsgLen / commits.size());

            long nightEvents = commits.stream().filter(c -> c.getCommittedAt().getHour() < 6).count() +
                    tasks.stream().filter(t -> t.getUpdatedAt().getHour() < 6).count();
            metric.setNightWorkSeconds((int) nightEvents * 3600);

            boolean isWeekend = yesterday.getDayOfWeek().getValue() >= 6;
            metric.setWeekendWorkSeconds(isWeekend ? 8 * 3600 : 0);

            LocalDateTime firstEvent = getFirstEvent(commits, tasks, prs, endOfDay);
            LocalDateTime lastEvent = getLastEvent(commits, tasks, prs, startOfDay);

            if (firstEvent.isBefore(lastEvent)) {
                long workSeconds = ChronoUnit.SECONDS.between(firstEvent, lastEvent);
                metric.setTotalWorkSeconds(workSeconds < 3600 ? 4 * 3600 : (int) workSeconds);
            } else {
                metric.setTotalWorkSeconds(8 * 3600);
            }

            double avgPrLead = prs.stream().mapToInt(GitPullRequest::getLeadTimeMinutes).average().orElse(0.0);
            metric.setPrLeadTimeAvg((int) avgPrLead);

            long stagnantTasks = tasks.stream()
                    .filter(t -> {
                        String status = t.getStatus();
                        return status != null && !status.equalsIgnoreCase("Done") && !status.equalsIgnoreCase("Готово");
                    })
                    .count();
            metric.setTaskStagnationSeconds((int) stagnantTasks * 3600);

            long reopenedTasks = tasks.stream()
                    .filter(t -> {
                        String status = t.getStatus();
                        return status != null && (status.equalsIgnoreCase("In Progress") || status.equalsIgnoreCase("В работе"));
                    })
                    .count();
            metric.setReopenRate((int) reopenedTasks);

            dailyMetricRepository.save(metric);
            metricsCreated++;
        }

        log.info("Агрегация завершена. Сформировано/обновлено {} метрик за вчерашний день.", metricsCreated);
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
