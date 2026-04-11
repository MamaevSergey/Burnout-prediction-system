package com.lamart.burnout.burnoutpredictionsystem.service.etl.mock;

import com.lamart.burnout.burnoutpredictionsystem.entity.*;
import com.lamart.burnout.burnoutpredictionsystem.repository.*;
import com.lamart.burnout.burnoutpredictionsystem.service.scoring.ScoringEngineService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "app.mock-data.enabled", havingValue = "true")
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
    private final Random random = new Random();

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDatabase() {
        if (employeeRepository.count() > 0) {
            log.info("База данных уже содержит сотрудников, Mock-данные не будут загружены.");
            return;
        }

        log.info("Начинаем генерацию Mock-данных...");

        seedBaselineMlModel();

        List<Team> teams = teamRepository.saveAll(orgFactory.createTeams());
        List<Project> projects = projectRepository.saveAll(orgFactory.createProjects());
        List<EmployeeProfile> profiles = orgFactory.createEmployees(teams);

        employeeRepository.saveAll(profiles.stream().map(EmployeeProfile::employee).toList());

        generateAndSaveLogs(profiles, projects);

        log.info("Генерация Mock-данных успешно завершена! Данные сохранены.");
        scoringEngineService.calculateScores(LocalDate.now());
    }

    private void seedBaselineMlModel() {
        if (mlModelRepository.count() > 0) {
            log.info("ML-модели уже существуют в базе. Пропускаем инициализацию базовой модели.");
            return;
        }

        MlModel initialModel = new MlModel();
        initialModel.setW0Bias(-2.5);
        initialModel.setW1Ee(1.2);
        initialModel.setW2Dp(0.8);
        initialModel.setW3Rpa(0.5);
        initialModel.setActive(true);
        initialModel.setTrainedAt(LocalDateTime.now());

        mlModelRepository.save(initialModel);
        log.info("Базовая ML-модель успешно инициализирована.");
    }

    private void generateAndSaveLogs(List<EmployeeProfile> profiles, List<Project> projects) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(40);
        Random random = new Random();
        AtomicInteger taskSequence = new AtomicInteger(1);

        List<GitCommit> allCommits = new ArrayList<>();
        List<GitPullRequest> allPrs = new ArrayList<>();
        List<JiraTask> allTasks = new ArrayList<>();
        List<JiraTaskComment> allComments = new ArrayList<>();
        List<JiraTaskChangelog> allChangelogs = new ArrayList<>();
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
                allComments.addAll(result.comments());
                allChangelogs.addAll(result.changelogs());
                allMetrics.add(result.dailyMetric());
            }
        }

        log.info("Сохраняем сырые данные: {} коммитов, {} PR, {} задач...", allCommits.size(), allPrs.size(), allTasks.size());
        gitCommitRepository.saveAll(allCommits);
        gitPullRequestRepository.saveAll(allPrs);
        jiraTaskRepository.saveAll(allTasks);

        log.info("Сохраняем сырые активности Jira: {} комментариев, {} смен статусов...", allComments.size(), allChangelogs.size());
        jiraTaskCommentRepository.saveAll(allComments);
        jiraTaskChangelogRepository.saveAll(allChangelogs);

        log.info("Сохраняем агрегированные метрики: {} дней работы...", allMetrics.size());
        dailyMetricRepository.saveAll(allMetrics);
    }
}
