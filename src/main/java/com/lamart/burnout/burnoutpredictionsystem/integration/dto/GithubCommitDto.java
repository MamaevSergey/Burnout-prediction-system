package com.lamart.burnout.burnoutpredictionsystem.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.time.ZonedDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubCommitDto {
    private String sha;
    private CommitInfo commit;
    private UserInfo author;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommitInfo {
        private String message;
        private GitUser committer;
        private GitUser author;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitUser {
        private String name;
        private String email;
        private ZonedDateTime date;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserInfo {
        private String login;
    }
}
