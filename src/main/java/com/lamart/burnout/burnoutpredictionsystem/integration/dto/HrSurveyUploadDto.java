package com.lamart.burnout.burnoutpredictionsystem.integration.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

@Data
public class HrSurveyUploadDto {
    @NotNull
    @NotEmpty
    private List<SurveyResult> results;

    @Data
    public static class SurveyResult {
        @NotNull
        private String email; // Убрал UUID и поставил String (email), т.к. HR не знает UUID сотрудника, только его почту.
        private int isBurnedOut; // 1 - зона риска, 0 - в норме
    }
}
