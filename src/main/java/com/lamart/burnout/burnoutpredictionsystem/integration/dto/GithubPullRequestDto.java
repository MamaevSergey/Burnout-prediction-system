package com.lamart.burnout.burnoutpredictionsystem.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubPullRequestDto {
    private Long number;
    private String state;

    @JsonProperty("created_at")
    private ZonedDateTime createdAt;

    @JsonProperty("merged_at")
    private ZonedDateTime mergedAt;

    @JsonProperty("updated_at")
    private ZonedDateTime updatedAt;

    private AuthorInfo user;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AuthorInfo {
        private String login;
    }
}
