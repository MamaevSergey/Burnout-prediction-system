package com.lamart.burnout.burnoutpredictionsystem.service.etl.mock;

import com.lamart.burnout.burnoutpredictionsystem.entity.*;
import com.lamart.burnout.burnoutpredictionsystem.repository.*;
import com.lamart.burnout.burnoutpredictionsystem.service.scoring.ScoringEngineService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockDataSeeder {
    private final TeamRepository teamRepository;
    private final ProjectRepository projectRepository;
    private final EmployeeRepository employeeRepository;
    private final GitCommitRepository gitCommitRepository;
    private final GitPullRequestRepository gitPullRequestRepository;
    private final JiraTaskRepository jiraTaskRepository;
    private final DailyMetricRepository dailyMetricRepository;
    private final ScoringEngineService scoringEngineService;
    private final MockOrganizationFactory orgFactory;
    private final MockActivityFactory activityFactory;
    private final MlModelRepository mlModelRepository;
    private final Random random = new Random();

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDatabase() {
        if (employeeRepository.count() > 0) {
            log.info("База уже наполнена. Пропуск генерации.");
            return;
        }

        log.info("Запуск симуляции ETL...");

        seedBaselineMlModel();

        List<Team> teams = teamRepository.saveAll(orgFactory.createTeams());
        List<Project> projects = projectRepository.saveAll(orgFactory.createProjects());

        List<EmployeeProfile> profiles = orgFactory.createEmployees(teams);
        employeeRepository.saveAll(profiles.stream().map(EmployeeProfile::employee).toList());

        generateAndSaveLogs(profiles, projects);

        log.info("Симуляция успешно завершена! Данные сохранены.");
        log.info("Запуск первичного расчета выгорания по сгенерированным данным...");
        scoringEngineService.calculateScoresForToday();
    }

    private void seedBaselineMlModel() {
        if (mlModelRepository.count() == 0) {
            MlModel baselineModel = new MlModel();
            baselineModel.setW0Bias(-1.0);
            baselineModel.setW1Ee(2.5);
            baselineModel.setW2Dp(2.0);
            baselineModel.setW3Rpa(1.5);
            baselineModel.setActive(true);
            baselineModel.setTrainedAt(LocalDateTime.now());

            mlModelRepository.save(baselineModel);
            log.info("Базова ML-модель успешно загружена в БД.");
        }
    }

    private void generateAndSaveLogs(List<EmployeeProfile> profiles, List<Project> projects) {
        LocalDate startDate = LocalDate.now().minusDays(30);
        LocalDate endDate = LocalDate.now();

        AtomicInteger taskSequence = new AtomicInteger(1000);

        List<GitCommit> allCommits = new ArrayList<>();
        List<GitPullRequest> allPrs = new ArrayList<>();
        List<JiraTask> allTasks = new ArrayList<>();
        List<DailyMetric> allMetrics = new ArrayList<>();

        for (LocalDate date = startDate; date.isBefore(endDate); date = date.plusDays(1)) {
            boolean isWeekend = date.getDayOfWeek().getValue() >= 6;

            for (EmployeeProfile profile : profiles) {
                if (isWeekend && profile.type() != ProfileType.EE_EXHAUSTED && random.nextDouble() > 0.1) {
                    continue;
                }

                Project proj = projects.get(random.nextInt(projects.size()));

                DailySimulationResult result = activityFactory.generateDailyActivity(
                        profile, proj, date, isWeekend, taskSequence.getAndIncrement()
                );

                allCommits.addAll(result.commits());
                allPrs.add(result.pullRequest());
                allTasks.add(result.jiraTask());
                allMetrics.add(result.dailyMetric());
            }
        }

        log.info("Сохраняем сырые данные: {} коммитов, {} PR, {} задач...", allCommits.size(), allPrs.size(), allTasks.size());
        gitCommitRepository.saveAll(allCommits);
        gitPullRequestRepository.saveAll(allPrs);
        jiraTaskRepository.saveAll(allTasks);

        log.info("Сохраняем агрегированные метрики: {} дней работы...", allMetrics.size());
        dailyMetricRepository.saveAll(allMetrics);
    }
}
