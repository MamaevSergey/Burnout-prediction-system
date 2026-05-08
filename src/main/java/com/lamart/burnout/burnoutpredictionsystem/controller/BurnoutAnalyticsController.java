package com.lamart.burnout.burnoutpredictionsystem.controller;

import com.lamart.burnout.burnoutpredictionsystem.dto.DashboardSummaryDto;
import com.lamart.burnout.burnoutpredictionsystem.dto.EmployeeDetailDto;
import com.lamart.burnout.burnoutpredictionsystem.dto.EmployeeSummaryDto;
import com.lamart.burnout.burnoutpredictionsystem.service.AnalyticsService;
import com.lamart.burnout.burnoutpredictionsystem.service.scoring.BurnoutAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/burnout/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BurnoutAnalyticsController {
    private final BurnoutAnalysisService analysisService;
    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public DashboardSummaryDto getSummary() {
        return analyticsService.getDashboardSummary();
    }

    @GetMapping("/details/{employeeId}")
    public EmployeeDetailDto getDetails(@PathVariable UUID employeeId) {
        return analysisService.getEmployeeDetail(employeeId);
    }
}