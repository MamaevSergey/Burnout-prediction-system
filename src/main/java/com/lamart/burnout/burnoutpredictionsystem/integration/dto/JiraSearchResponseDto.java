package com.lamart.burnout.burnoutpredictionsystem.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraSearchResponseDto {
    private int total;
    private List<JiraIssueDto> issues;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraIssueDto {
        private String key;
        private IssueFields fields;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IssueFields {
        private String created;
        private String updated;
        private StatusDto status;
        private AssigneeDto assignee;
        private CommentPageDto comment;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatusDto {
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssigneeDto {
        private String emailAddress;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommentPageDto {
        private int total;
    }
}
