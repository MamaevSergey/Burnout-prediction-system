package com.lamart.burnout.burnoutpredictionsystem.dto;

public record JwtResponseDto(
        String token,
        String type,
        String username)
{}
