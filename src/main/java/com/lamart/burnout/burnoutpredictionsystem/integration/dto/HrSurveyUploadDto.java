package com.lamart.burnout.burnoutpredictionsystem.integration.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class HrSurveyUploadDto {
    @NotNull
    @NotEmpty
    @Valid // Проверка каждого элемент списка
    private List<SurveyResult> results;

    @Data
    public static class SurveyResult {
        @NotBlank(message = "Email не может быть пустым")
        @Email(message = "Некорректный формат email")
        private String email; // Убрал UUID и поставил String (email), т.к. HR не знает UUID сотрудника, только его почту.

        @Min(value = 0, message = "Значение должно быть 0 или 1")
        @Max(value = 1, message = "Значение должно быть 0 или 1")
        private int isBurnedOut; // 1 - зона риска, 0 - в норме
    }
}
