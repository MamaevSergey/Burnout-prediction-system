package com.lamart.burnout.burnoutpredictionsystem.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class BackfillRequestDto {
    @NotNull(message = "Дата начала не может быть пустой")
    private LocalDate startDate;

    @NotNull(message = "Дата окончания не может быть пустой")
    private LocalDate endDate;
}