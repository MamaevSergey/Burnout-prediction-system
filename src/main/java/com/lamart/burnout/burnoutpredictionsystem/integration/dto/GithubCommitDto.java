package com.lamart.burnout.burnoutpredictionsystem.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubCommitDto {
    private String sha;
    private CommitInfo commit;
    private AuthorInfo author;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommitInfo {
        private String message;
        private CommitterInfo committer;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommitterInfo {
        private LocalDateTime date;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AuthorInfo {
        private String login;
    }
}
