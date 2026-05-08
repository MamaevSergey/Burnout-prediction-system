package com.lamart.burnout.burnoutpredictionsystem.service.etl;

import com.lamart.burnout.burnoutpredictionsystem.integration.client.EtlProcessorService;
import com.lamart.burnout.burnoutpredictionsystem.service.scoring.ScoringEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackfillService {
    private final EtlProcessorService etlProcessorService;
    private final MetricAggregationService aggregationService;
    private final ScoringEngineService scoringEngineService;

    @Async
    public CompletableFuture<Void> runHistoricalBackfill(LocalDate startDate, LocalDate endDate) {
        log.info("Начат процесс Backfill (ретроспективный сбор) с {} по {}", startDate, endDate);

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            log.info("=== Запуск ETL для даты: {} ===", date);

            boolean success = false;
            int attempts = 0;
            int maxAttempts = 3;

            while (!success && attempts < maxAttempts) {
                try {
                    etlProcessorService.syncGithubCommits(date);
                    Thread.sleep(1000);

                    etlProcessorService.syncJiraTasks(date);
                    Thread.sleep(1000);

                    etlProcessorService.syncGithubPullRequests(date);
                    Thread.sleep(1000);

                    aggregationService.aggregateMetricsForDate(date);
                    scoringEngineService.calculateScores(date);

                    success = true;
                    log.info("Успешно завершена обработка за {}", date);
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    log.warn("Процесс backfill был прерван");
                    Thread.currentThread().interrupt();
                    return CompletableFuture.completedFuture(null);
                } catch (Exception e) {
                    attempts++;
                    log.warn("Сбой при сборе данных за {} (попытка {} из {}): {}", date, attempts, maxAttempts, e.getMessage());

                    if (attempts >= maxAttempts) {
                        log.error("Пропуск даты {} после {} неудачных попыток. Идем дальше.", date, maxAttempts);
                    } else {
                        try {
                            log.info("Ждем 15 секунд перед повторной попыткой...");
                            Thread.sleep(15000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }

        log.info("Процесс Backfill успешно завершен!");
        return CompletableFuture.completedFuture(null);
    }
}