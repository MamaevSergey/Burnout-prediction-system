package com.lamart.burnout.burnoutpredictionsystem.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MappingRequestDto {
    @NotBlank
    @Email
    private String email;

    @Pattern(regexp = "^([a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38})?$", message = "Недопустимый формат GitHub Username")
    private String githubUsername;

    @JsonProperty("isActive")
    private Boolean isActive;
}