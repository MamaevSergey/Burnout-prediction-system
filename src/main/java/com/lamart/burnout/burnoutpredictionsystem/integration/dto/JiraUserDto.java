package com.lamart.burnout.burnoutpredictionsystem.integration.dto;

import lombok.Data;

@Data
public class JiraUserDto {
    private String accountId;
    private String accountType;
    private String displayName;
    private String emailAddress;
    private boolean active;
}