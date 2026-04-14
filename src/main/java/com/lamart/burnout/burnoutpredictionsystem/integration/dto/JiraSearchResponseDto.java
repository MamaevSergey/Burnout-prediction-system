package com.lamart.burnout.burnoutpredictionsystem.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraSearchResponseDto {
    private List<JiraIssueDto> issues;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraIssueDto {
        private String key;
        private IssueFields fields;
        private ChangelogDto changelog;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IssueFields {
        private String created;
        private String updated;
        private StatusDto status;
        private AssigneeDto assignee;
        private CommentPageDto comment;
        private List<AttachmentDto> attachment;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AttachmentDto {
        private AssigneeDto author;
        private String created;
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
        private List<CommentDto> comments;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommentDto {
        private AssigneeDto author;

        @JsonProperty("body")
        private java.util.Map<String, Object> rawBody;

        private String created;

        @com.fasterxml.jackson.annotation.JsonIgnore
        public String getBody() {
            return rawBody != null ? rawBody.toString() : "";
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChangelogDto {
        private List<HistoryDto> histories;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HistoryDto {
        private String created;
        private List<HistoryItemDto> items;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HistoryItemDto {
        private String field;
        private String fromString;
        private String toString;
    }
}
