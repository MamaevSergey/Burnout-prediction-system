package com.lamart.burnout.burnoutpredictionsystem.service.etl.mock;

import com.lamart.burnout.burnoutpredictionsystem.entity.*;
import com.lamart.burnout.burnoutpredictionsystem.repository.*;
import com.lamart.burnout.burnoutpredictionsystem.service.etl.MetricAggregationService;
import com.lamart.burnout.burnoutpredictionsystem.service.scoring.ScoringEngineService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
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
@Profile("mock")
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
    private final JiraTaskCommentRepository jiraTaskCommentRepository;
    private final JiraTaskChangelogRepository jiraTaskChangelogRepository;
    private static final int SIMULATION_DAYS = 90;
    private static final Random random = new Random();
    private final MetricAggregationService aggregationService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDatabase() {
        if (employeeRepository.count() > 0) {
            log.info("База данных уже содержит сотрудников — Mock-данные не будут загружены.");
            return;
        }

        log.info("Начало генерации Mock-данных (90 дней)");

        seedBaselineMlModel();

        List<Team> teams = teamRepository.saveAll(orgFactory.createTeams());
        log.info("Команды созданы: {}", teams.size());

        List<Project> projects = projectRepository.saveAll(orgFactory.createProjects());
        log.info("Проекты созданы: {}", projects.size());

        List<EmployeeProfile> profiles = orgFactory.createEmployees(teams);
        employeeRepository.saveAll(profiles.stream().map(EmployeeProfile::employee).toList());
        log.info("Сотрудники созданы: {}", profiles.size());
        logProfileDistribution(profiles);

        generateAndSaveLogs(profiles, projects);

        log.info("Сырые данные успешно сгенерированы! Запускаем реальный пайплайн обработки...");

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(SIMULATION_DAYS);

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            aggregationService.aggregateMetricsForDate(date);
            scoringEngineService.calculateScores(date, true);
        }

        log.info("Инициализация системы завершена. Все метрики и баллы посчитаны на реальных данных.");
    }

    private void seedBaselineMlModel() {
        if (mlModelRepository.count() > 0) {
            log.info("ML-модель уже существует — пропускаем инициализацию.");
            return;
        }
        MlModel model = new MlModel();
        model.setW0Bias(-0.8);
        model.setW1Ee(1.2);
        model.setW2Dp(1.0);
        model.setW3Rpa(1.0);
        model.setVersion("mock-v1.0-" + LocalDate.now());
        model.setActive(true);
        model.setTrainedAt(LocalDateTime.now());
        mlModelRepository.save(model);
        log.info("Базовая ML-модель инициализирована.");
    }

    private void generateAndSaveLogs(List<EmployeeProfile> profiles, List<Project> projects) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(SIMULATION_DAYS);
        AtomicInteger taskSequence = new AtomicInteger(1000);

        List<GitCommit> allCommits = new ArrayList<>();
        List<GitPullRequest> allPrs = new ArrayList<>();
        List<JiraTask> allTasks = new ArrayList<>();
        List<JiraTaskComment> allComments = new ArrayList<>();
        List<JiraTaskChangelog> allChangelogs = new ArrayList<>();

        int day = 0;

        for (LocalDate date = startDate; date.isBefore(endDate); date = date.plusDays(1)) {
            day++;
            boolean isWeekend = date.getDayOfWeek().getValue() >= 6;

            double globalPhase = (double) day / SIMULATION_DAYS;

            for (EmployeeProfile profile : profiles) {
                if (isWeekend && !shouldWorkWeekend(profile, globalPhase)) {
                    continue;
                }

                double effectivePhase = computeEffectivePhase(profile.type(), globalPhase);

                Project proj = projects.get(random.nextInt(projects.size()));

                DailySimulationResult result = activityFactory.generateDailyActivity(
                        profile, proj, date, isWeekend, effectivePhase
                );

                allCommits.addAll(result.commits());
                allPrs.addAll(result.pullRequests());
                allTasks.addAll(result.jiraTasks());
                allComments.addAll(result.comments());
                allChangelogs.addAll(result.changelogs());
            }
        }

        log.info("Сохраняем {} коммитов, {} PR, {} задач...", allCommits.size(), allPrs.size(), allTasks.size());
        gitCommitRepository.saveAll(allCommits);
        gitPullRequestRepository.saveAll(allPrs);
        jiraTaskRepository.saveAll(allTasks);

        log.info("Сохраняем {} комментариев Jira, {} смен статусов...", allComments.size(), allChangelogs.size());
        jiraTaskCommentRepository.saveAll(allComments);
        jiraTaskChangelogRepository.saveAll(allChangelogs);
    }

    private boolean shouldWorkWeekend(EmployeeProfile profile, double phase) {
        return switch (profile.type()) {
            case EE_EXHAUSTED -> random.nextDouble() < 0.6 + phase * 0.3;
            case BURNED_OUT   -> random.nextDouble() < 0.2 + phase * 0.3;
            case ELEVATED_RISK -> random.nextDouble() < 0.1 + phase * 0.1;
            default -> random.nextDouble() < 0.05;
        };
    }

    private double computeEffectivePhase(ProfileType type, double globalPhase) {
        double spike = Math.pow(globalPhase, 4);
        return switch (type) {
            case NORMAL        -> spike * 0.15;
            case ELEVATED_RISK -> 0.1 + spike * 0.4;
            case EE_EXHAUSTED  -> 0.2 + spike * 0.8;
            case DP_CYNICAL    -> 0.15 + spike * 0.85;
            case RPA_STAGNANT  -> 0.1 + spike * 0.9;
            case BURNED_OUT    -> 0.3 + spike * 0.7;
        };
    }

    private void logProfileDistribution(List<EmployeeProfile> profiles) {
        var counts = new java.util.EnumMap<ProfileType, Integer>(ProfileType.class);
        for (ProfileType t : ProfileType.values()) counts.put(t, 0);
        profiles.forEach(p -> counts.merge(p.type(), 1, Integer::sum));

        log.info("Распределение профилей:");
        counts.forEach((type, count) ->
                log.info("  {}: {} чел. ({}%)", type.name(), count, Math.round(count * 100.0 / profiles.size()))
        );
    }
}