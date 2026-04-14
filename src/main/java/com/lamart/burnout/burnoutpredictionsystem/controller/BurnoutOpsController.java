package com.lamart.burnout.burnoutpredictionsystem.controller;

import com.lamart.burnout.burnoutpredictionsystem.dto.ModelWeightsUpdateDto;
import com.lamart.burnout.burnoutpredictionsystem.entity.Employee;
import com.lamart.burnout.burnoutpredictionsystem.entity.MlModel;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.HrSurveyUploadDto;
import com.lamart.burnout.burnoutpredictionsystem.repository.EmployeeRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.MlModelRepository;
import com.lamart.burnout.burnoutpredictionsystem.scheduler.DailyEtlScheduler;
import com.lamart.burnout.burnoutpredictionsystem.service.scoring.ModelTrainingService;
import com.lamart.burnout.burnoutpredictionsystem.service.scoring.ScoringEngineService;
import com.lamart.burnout.burnoutpredictionsystem.util.Anonymizer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/burnout/ops")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BurnoutOpsController {
    private final MlModelRepository mlModelRepository;
    private final DailyEtlScheduler dailyEtlScheduler;
    private final ModelTrainingService modelTrainingService;
    private final ScoringEngineService scoringEngineService;
    private final Anonymizer anonymizer;
    private final EmployeeRepository employeeRepository;

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
    public ResponseEntity<String> forceRunEtl() {
        dailyEtlScheduler.runNightlyPipeline();
        return ResponseEntity.ok("Ночной пайплайн запущен!");
    }

    @PostMapping("/train")
    public ResponseEntity<String> trainModel(@Valid @RequestBody HrSurveyUploadDto surveyDto) {
        modelTrainingService.prepareDatasetAndTrain(surveyDto);
        scoringEngineService.calculateScores(LocalDate.now().minusDays(1));
        return ResponseEntity.ok("Модель успешно переобучена и активирована");
    }

    @PostMapping(value = "/train/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> trainModelFromCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Файл не должен быть пустым!");
        }
        modelTrainingService.processCsvAndTrain(file);
        scoringEngineService.calculateScores(LocalDate.now().minusDays(1));
        return ResponseEntity.ok("CSV файл успешно обработан. Модель переобучена. Риски пересчитаны.");
    }

    @PostMapping("/map-github")
    public ResponseEntity<String> mapGitHubAccounts(@RequestBody List<Map<String, String>> mappings) {
        int count = 0;
        for (Map<String, String> entry : mappings) {
            String email = entry.get("email");
            String githubUsername = entry.get("githubUsername");

            if (email != null && githubUsername != null) {
                UUID hashedId = anonymizer.hashToUuid(email);

                Employee emp = employeeRepository.findById(hashedId).orElseGet(() -> {
                    Employee newEmp = new Employee();
                    newEmp.setId(hashedId);
                    return newEmp;
                });

                emp.setGithubUsername(githubUsername);
                employeeRepository.save(emp);
                count++;
            }
        }
        return ResponseEntity.ok("Обновлено связей: " + count);
    }
}
