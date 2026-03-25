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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricAggregationService {

    private final EmployeeRepository employeeRepository;
    private final GitCommitRepository gitCommitRepository;
    private final JiraTaskRepository jiraTaskRepository;
    private final DailyMetricRepository dailyMetricRepository;

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

            if (commits.isEmpty() && tasks.isEmpty()) continue;

            DailyMetric metric = new DailyMetric();
            metric.setEmployee(emp);
            metric.setDate(yesterday);

            int totalMsgLen = commits.stream().mapToInt(GitCommit::getMessageLength).sum();
            metric.setAvgCommitMsgLen(commits.isEmpty() ? 0 : totalMsgLen / commits.size());

            long nightEvents = commits.stream().filter(c -> c.getCommittedAt().getHour() < 6).count() +
                    tasks.stream().filter(t -> t.getUpdatedAt().getHour() < 6).count();
            metric.setNightWorkSeconds((int) nightEvents * 3600);

            metric.setTotalWorkSeconds(8 * 3600);

            boolean isWeekend = yesterday.getDayOfWeek().getValue() >= 6;
            metric.setWeekendWorkSeconds(isWeekend ? 8 * 3600 : 0);

            dailyMetricRepository.save(metric);
            metricsCreated++;
        }

        log.info("Агрегация завершена. Создано {} метрик за вчерашний день.", metricsCreated);
    }
}
