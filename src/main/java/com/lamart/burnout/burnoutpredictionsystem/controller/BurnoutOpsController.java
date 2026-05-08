package com.lamart.burnout.burnoutpredictionsystem.controller;

import com.lamart.burnout.burnoutpredictionsystem.dto.BackfillRequestDto;
import com.lamart.burnout.burnoutpredictionsystem.dto.ModelWeightsUpdateDto;
import com.lamart.burnout.burnoutpredictionsystem.entity.Employee;
import com.lamart.burnout.burnoutpredictionsystem.entity.MlModel;
import com.lamart.burnout.burnoutpredictionsystem.integration.client.EtlProcessorService;
import com.lamart.burnout.burnoutpredictionsystem.integration.client.JiraApiClient;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.HrSurveyUploadDto;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.JiraUserDto;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.JiraUserMappingDto;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.MappingRequestDto;
import com.lamart.burnout.burnoutpredictionsystem.repository.EmployeeRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.MlModelRepository;
import com.lamart.burnout.burnoutpredictionsystem.scheduler.DailyEtlScheduler;
import com.lamart.burnout.burnoutpredictionsystem.service.etl.BackfillService;
import com.lamart.burnout.burnoutpredictionsystem.service.etl.MetricAggregationService;
import com.lamart.burnout.burnoutpredictionsystem.service.scoring.ModelTrainingService;
import com.lamart.burnout.burnoutpredictionsystem.service.scoring.ScoringEngineService;
import com.lamart.burnout.burnoutpredictionsystem.util.Anonymizer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/burnout/ops")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class BurnoutOpsController {
    private final MlModelRepository mlModelRepository;
    private final DailyEtlScheduler dailyEtlScheduler;
    private final ModelTrainingService modelTrainingService;
    private final ScoringEngineService scoringEngineService;
    private final Anonymizer anonymizer;
    private final EmployeeRepository employeeRepository;
    private final JiraApiClient jiraApiClient;
    private final BackfillService backfillService;

    private final EtlProcessorService etlProcessorService;
    private final MetricAggregationService aggregationService;

    /*
    @Value("${spring.profiles.active}")
    private String isMock;
     */

    @PostMapping("/weights")
    public ResponseEntity<String> updateModelWeights(@Valid @RequestBody ModelWeightsUpdateDto dto) {
        modelTrainingService.deactivateCurrentModel();

        MlModel newModel = new MlModel();
        newModel.setW0Bias(dto.w0());
        newModel.setW1Ee(dto.w1());
        newModel.setW2Dp(dto.w2());
        newModel.setW3Rpa(dto.w3());
        newModel.setActive(true);
        newModel.setTrainedAt(LocalDateTime.now());
        mlModelRepository.save(newModel);

        return ResponseEntity.ok("Новые веса модели успешно применены. Начиная с завтрашнего дня расчет будет идти по ним.");
    }

    @GetMapping("/test-etl")
    public ResponseEntity<String> forceRunEtl(@RequestParam(required = false) String date) {
        LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        log.info("Ручной запуск ETL-пайплайна за дату: {}", targetDate);

        etlProcessorService.syncGithubCommits(targetDate);
        etlProcessorService.syncJiraTasks(targetDate);
        etlProcessorService.syncGithubPullRequests(targetDate);

        aggregationService.aggregateMetricsForDate(targetDate);
        scoringEngineService.calculateScores(targetDate);

        return ResponseEntity.ok("Ручной пайплайн за " + targetDate + "успешно запущен!");
    }

    @PostMapping("/train")
    public ResponseEntity<String> trainModel(@Valid @RequestBody HrSurveyUploadDto surveyDto) {
        modelTrainingService.prepareDatasetAndTrain(surveyDto);

        recalculateHistoryAfterTraining();

        return ResponseEntity.ok("Модель успешно переобучена и активирована");
    }

    @PostMapping("/backfill")
    public ResponseEntity<String> triggerBackfill(@Valid @RequestBody BackfillRequestDto request) {
        if (request.getStartDate().isAfter(request.getEndDate())) {
            return ResponseEntity.badRequest().body("Ошибка: Дата начала не может быть позже даты окончания.");
        }
        if (request.getEndDate().isAfter(LocalDate.now())) {
            return ResponseEntity.badRequest().body("Ошибка: Нельзя собрать данные за будущие даты.");
        }

        backfillService.runHistoricalBackfill(request.getStartDate(), request.getEndDate());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Процесс Backfill успешно запущен в фоновом режиме. " +
                        "Сбор данных с " + request.getStartDate() + " по " + request.getEndDate() + " займет некоторое время.");
    }

    @PostMapping(value = "/train/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> trainModelFromCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Файл не должен быть пустым!");
        }
        modelTrainingService.processCsvAndTrain(file);

        recalculateHistoryAfterTraining();

        return ResponseEntity.ok("CSV файл успешно обработан. Модель переобучена. Риски пересчитаны.");
    }

    private void recalculateHistoryAfterTraining() {
        LocalDate today = LocalDate.now();
        log.info("Запуск перерасчета скоринга за последние 30 дней с новыми весами...");
        for (int i = 30; i >= 1; i--) {
            scoringEngineService.calculateScores(today.minusDays(i));
        }
        log.info("Перерасчет истории успешно завершен.");
    }

    @PostMapping("/map-github")
    public ResponseEntity<String> mapGitHubAccounts(@RequestBody List<MappingRequestDto> mappings) {
        int count = 0;
        for (MappingRequestDto entry : mappings) {
            if (entry.getEmail() == null) continue;

            UUID hashedId = anonymizer.hashToUuid(entry.getEmail());

            if (entry.getGithubUsername() != null && !entry.getGithubUsername().isBlank()) {
                Optional<Employee> existingByGit = employeeRepository.findByGithubUsername(entry.getGithubUsername());
                if (existingByGit.isPresent() && !existingByGit.get().getId().equals(hashedId)) {
                    return ResponseEntity.badRequest().body("Ошибка: github_username '" + entry.getGithubUsername() + "' уже привязан!");
                }
            }

            Employee emp = employeeRepository.findById(hashedId).orElseGet(() -> {
                Employee newEmp = new Employee();
                newEmp.setId(hashedId);
                newEmp.setRole("Сотрудник");
                return newEmp;
            });

            emp.setGithubUsername(entry.getGithubUsername());
            if (entry.getIsActive() != null) emp.setActive(entry.getIsActive());

            employeeRepository.save(emp);
            count++;
        }
        return ResponseEntity.ok("Успешно обновлено сотрудников: " + count);
    }

    @GetMapping("/test-etl-60")
    public ResponseEntity<String> forceRunEtl60Days() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(60);

        log.info("Бысрый ручной запуск Blackfill за последние 60 дней: с {} по {}", endDate, startDate);

        backfillService.runHistoricalBackfill(startDate, endDate);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Запущен сбор логов за 60 дней (с " + startDate + " по " + endDate + "). " +
                "Первые 30 дней уйдут на сбор базы, последние 30 дней сформируют точные оценки риска.");
    }

    @Transactional
    @PostMapping(value = "/map-github/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> mapGitHubAccountsFromCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body("Файл не должен быть пустым!");
        int successCount = 0;
        int errorCount = 0;
        String githubRegex = "^[a-zA-Z0-9-]{1,39}$";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) { isFirstLine = false; continue; }
                String[] columns = line.split("[,;]");

                if (columns.length >= 2) {
                    String email = columns[0].replace("\uFEFF", "").trim().toLowerCase();
                    String githubUsername = columns[1].trim();

                    if (email.isBlank() || githubUsername.isBlank()) { errorCount++; continue; }

                    if (!githubUsername.matches(githubRegex)) {
                        log.warn("Пропущен невалидный Github логин: {} для {}", githubUsername, email);
                        errorCount++;
                        continue;
                    }

                    UUID hashedId = anonymizer.hashToUuid(email);
                    Optional<Employee> existingByGit = employeeRepository.findByGithubUsername(githubUsername);

                    if (existingByGit.isPresent() && !existingByGit.get().getId().equals(hashedId)) {
                        log.warn("Github логин {} уже привязан к другому сотруднику. Строка пропущена.", githubUsername);
                        errorCount++;
                        continue;
                    }

                    Employee employee = employeeRepository.findById(hashedId).orElseGet(() -> {
                        Employee newEmp = new Employee();
                        newEmp.setId(hashedId);
                        newEmp.setRole("Сотрудник");
                        return newEmp;
                    });

                    employee.setGithubUsername(githubUsername);
                    employee.setActive(true);

                    employeeRepository.save(employee);
                    successCount++;
                } else {
                    errorCount++;
                }
            }
        } catch (Exception exception) {
            log.error("Ошибка при обработке CSV файла маппинга: {}", exception.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка прои чтении файла: " + exception.getMessage());
        }

        if (successCount == 0 && errorCount > 0) return ResponseEntity.badRequest().body("Не удалось распознать данные. Проверье формат CSV файла.");
        String resultMessage = "CSV успешно обработан. Маппинг применился к: " + successCount + " сотрудникам.";

        if (errorCount > 0) {
            resultMessage += "\nПропущено строк с ошибками: " + errorCount + ".";
        }

        return ResponseEntity.ok(resultMessage);
    }

    @GetMapping("/jira-users")
    public ResponseEntity<List<JiraUserMappingDto>> getJiraUsersForMapping() {
        List<JiraUserDto> jiraUsers = new ArrayList<>(jiraApiClient.fetchAllJiraUsers());

        // Для теста (Mock-данные)
        /*
        if (jiraUsers.isEmpty() || isMock.equals("mock")) {
            log.info("Jira API недоступен. Генерируем 65 Mock-пользователей для экрана маппинга...");
            for (int i = 1; i <= 65; i++) {
                JiraUserDto mockUser = new JiraUserDto();
                mockUser.setEmailAddress("developer_" + i + "@lamart.ru");
                mockUser.setDisplayName("Lamart Developer " + i);
                mockUser.setActive(true);
                jiraUsers.add(mockUser);
            }
        }
         */

        List<JiraUserMappingDto> result = new ArrayList<>();

        for (JiraUserDto ju : jiraUsers) {
            if (ju.getEmailAddress() == null || ju.getEmailAddress().isBlank()) {
                continue;
            }

            String email = ju.getEmailAddress().toLowerCase().trim();
            UUID hashedId = anonymizer.hashToUuid(email);
            Optional<Employee> empOpt = employeeRepository.findById(hashedId);

            String githubUsername = null;
            boolean isMapped = false;
            boolean isActive = ju.isActive();

            if (empOpt.isPresent()) {
                githubUsername = empOpt.get().getGithubUsername();
                isMapped = githubUsername != null && !githubUsername.isBlank();
                isActive = empOpt.get().isActive();
            }

            if (!isActive) { continue; }

            result.add(new JiraUserMappingDto(
                    ju.getEmailAddress(),
                    ju.getDisplayName(),
                    isMapped,
                    githubUsername,
                    isActive
            ));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/recalculate/{employeeId}")
    public ResponseEntity<String> recalculateForEmployee(@PathVariable UUID employeeId) {
        LocalDate today = LocalDate.now();
        log.info("Глубокий пересчет метрик для сотрудника: {}", employeeId);

        for (int i = 30; i >= 1; i--) {
            LocalDate targetDate = today.minusDays(i);

            aggregationService.aggregateMetricsForDate(targetDate);
            scoringEngineService.calculateScoresForEmployee(targetDate, employeeId);
        }

        return ResponseEntity.ok("Баллы пересчитаны.");
    }
}