package com.lamart.burnout.burnoutpredictionsystem.controller;

import com.lamart.burnout.burnoutpredictionsystem.dto.EmployeeDetailDto;
import com.lamart.burnout.burnoutpredictionsystem.dto.EmployeeSummaryDto;
import com.lamart.burnout.burnoutpredictionsystem.entity.MlModel;
import com.lamart.burnout.burnoutpredictionsystem.repository.DailyMetricRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.MlModelRepository;
import com.lamart.burnout.burnoutpredictionsystem.scheduler.DailyEtlScheduler;
import com.lamart.burnout.burnoutpredictionsystem.service.scoring.BurnoutAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private final DailyMetricRepository dailyMetricRepository;
    private final DailyEtlScheduler dailyEtlScheduler;

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

        MlModel oldModel = mlModelRepository.findByIsActiveTrue();
        if (oldModel != null) {
            oldModel.setActive(false);
            mlModelRepository.save(oldModel);
        }

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
}