package com.lamart.burnout.burnoutpredictionsystem.dto;

import jakarta.validation.constraints.NotNull;

public record ModelWeightsUpdateDto (
        @NotNull(message = "w0 не может быть null!") Double w0,
        @NotNull(message = "w1 не может быть null!") Double w1,
        @NotNull(message = "w2 не может быть null!") Double w2,
        @NotNull(message = "w3 не может быть null!") Double w3
) {}