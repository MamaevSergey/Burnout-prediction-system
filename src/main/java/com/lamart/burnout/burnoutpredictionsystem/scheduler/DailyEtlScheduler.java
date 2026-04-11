package com.lamart.burnout.burnoutpredictionsystem.scheduler;

import com.lamart.burnout.burnoutpredictionsystem.integration.client.EtlProcessorService;
import com.lamart.burnout.burnoutpredictionsystem.service.etl.MetricAggregationService;
import com.lamart.burnout.burnoutpredictionsystem.service.scoring.ScoringEngineService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyEtlScheduler {

    private final EtlProcessorService etlProcessorService;
    private final MetricAggregationService aggregationService;
    private final ScoringEngineService scoringEngineService;

    @Scheduled(cron = "0 0 2 * * *")
    public void runNightlyPipeline() {
        log.info("Запуск ночного пайплайна аналитики");
        LocalDate targetDate = LocalDate.now().minusDays(1);

        etlProcessorService.syncGithubCommits();
        etlProcessorService.syncJiraTasks();
        etlProcessorService.syncGithubPullRequests();

        aggregationService.aggregateMetricForDate(targetDate);
        scoringEngineService.calculateScores(targetDate);

        log.info("Ночной пайплайн успешно завершен!");
    }
}
