package com.lamart.burnout.burnoutpredictionsystem.controller;

import com.lamart.burnout.burnoutpredictionsystem.dto.EmployeeDetailDto;
import com.lamart.burnout.burnoutpredictionsystem.dto.EmployeeSummaryDto;
import com.lamart.burnout.burnoutpredictionsystem.entity.MlModel;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.HrSurveyUploadDto;
import com.lamart.burnout.burnoutpredictionsystem.repository.DailyMetricRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.MlModelRepository;
import com.lamart.burnout.burnoutpredictionsystem.scheduler.DailyEtlScheduler;
import com.lamart.burnout.burnoutpredictionsystem.service.scoring.BurnoutAnalysisService;
import com.lamart.burnout.burnoutpredictionsystem.service.scoring.ModelTrainingService;
import com.lamart.burnout.burnoutpredictionsystem.service.scoring.ScoringEngineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/burnout")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BurnoutController {
    private final BurnoutAnalysisService analysisService;
    private final MlModelRepository mlModelRepository;
    private final DailyEtlScheduler dailyEtlScheduler;
    private final ModelTrainingService modelTrainingService;
    private final ScoringEngineService scoringEngineService;

    @GetMapping("/summary")
    public List<EmployeeSummaryDto> getSummary() {
        return analysisService.getAllEmployeesSummary();
    }

    @GetMapping("/details/{employeeId}")
    public EmployeeDetailDto getDetails(@PathVariable UUID employeeId) {
        return analysisService.getEmployeeDetail(employeeId);
    }

    @PostMapping("/weights")
    public ResponseEntity<String> updateModelWeights(
            @RequestParam double w0, @RequestParam double w1,
            @RequestParam double w2, @RequestParam double w3) {

        modelTrainingService.deactivateCurrentModel();

        MlModel newModel = new MlModel();
        newModel.setW0Bias(w0);
        newModel.setW1Ee(w1);
        newModel.setW2Dp(w2);
        newModel.setW3Rpa(w3);
        newModel.setActive(true);
        newModel.setTrainedAt(LocalDateTime.now());
        mlModelRepository.save(newModel);

        return ResponseEntity.ok("Новые веса модели успешно применены. Начиная с завтрашнего дня расчет будет идти по ним.");
    }

    @GetMapping("/test-etl")
    public ResponseEntity<String> forceRunEtl() {
        dailyEtlScheduler.runNightlyPipeline();
        return ResponseEntity.ok("Ночной пайплайн принудительно запущен! Информация по сбору данных будет отображена в логах");
    }

    @PostMapping("/train")
    public ResponseEntity<String> trainModel(@Valid @RequestBody HrSurveyUploadDto surveyDto) {
        modelTrainingService.prepareDatasetAndTrain(surveyDto);
        // Сразу идет пересчет текущих рисков с новыми весами.
        scoringEngineService.calculateScores(LocalDate.now().minusDays(1));
        return ResponseEntity.ok("Модель успешно переобучена и активирована");
    }

    @PostMapping(value = "/train/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> trainModelFromCsv(@RequestParam("file")MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Файл не должен быть пустым!");
        }
        try {
            modelTrainingService.processCsvAndTrain(file);
            scoringEngineService.calculateScores(LocalDate.now().minusDays(1));
            return ResponseEntity.ok("CSV файл успешно обработан. Модель переобучена. Риски пересчитаны.");
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body("Произошла ошибка: " + exception.getMessage());
        }
    }
}