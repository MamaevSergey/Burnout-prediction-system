package com.lamart.burnout.burnoutpredictionsystem.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TokenRefreshRequest {
    @NotBlank
    private String refreshToken;
}