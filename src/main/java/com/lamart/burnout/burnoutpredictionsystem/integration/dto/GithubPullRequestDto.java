package com.lamart.burnout.burnoutpredictionsystem.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubPullRequestDto {
    private Long number;
    private String state;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("merged_at")
    private LocalDateTime mergedAt;

    private AuthorInfo user;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AuthorInfo {
        private String login;
    }
}
