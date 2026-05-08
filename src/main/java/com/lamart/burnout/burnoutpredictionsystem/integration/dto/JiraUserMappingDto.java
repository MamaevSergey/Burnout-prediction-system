package com.lamart.burnout.burnoutpredictionsystem.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JiraUserMappingDto {
    private String email;
    private String displayName;

    @JsonProperty("isMapped")
    private boolean isMapped;

    private String githubUsername;

    @JsonProperty("isActive")
    private boolean isActive;
}