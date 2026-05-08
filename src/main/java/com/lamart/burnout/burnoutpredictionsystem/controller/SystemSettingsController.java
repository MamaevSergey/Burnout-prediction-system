package com.lamart.burnout.burnoutpredictionsystem.controller;

import com.lamart.burnout.burnoutpredictionsystem.entity.SystemSettings;
import com.lamart.burnout.burnoutpredictionsystem.repository.SystemSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SystemSettingsController {
    private final SystemSettingsRepository repository;

    @GetMapping
    public ResponseEntity<SystemSettings> getSettings() {
        SystemSettings settings = repository.findById(1L).orElseGet(() -> repository.save(new SystemSettings()));

        SystemSettings dto = new SystemSettings();
        dto.setGreenThreshold(settings.getGreenThreshold());
        dto.setYellowThreshold(settings.getYellowThreshold());
        dto.setScoringAlpha(settings.getScoringAlpha());
        dto.setJiraUrl(settings.getJiraUrl());
        dto.setGithubOwner(settings.getGithubOwner());
        dto.setJiraUsername(settings.getJiraUsername() != null ? "********" : "");
        dto.setTimezone(settings.getTimezone());
        dto.setMinCommitLength(settings.getMinCommitLength());
        dto.setBoilerplateWords(settings.getBoilerplateWords());
        dto.setDoneStatuses(settings.getDoneStatuses());
        dto.setIsolatedEventDurationSeconds(settings.getIsolatedEventDurationSeconds());

        dto.setJiraToken(settings.getJiraToken() != null ? "********" : "");
        dto.setGithubToken(settings.getGithubToken() != null ? "********" : "");

        return ResponseEntity.ok(dto);
    }

    @PutMapping
    public ResponseEntity<String> updateSettings(@RequestBody SystemSettings updated) {
        SystemSettings existing = repository.findById(1L).orElseGet(SystemSettings::new);

        existing.setGreenThreshold(updated.getGreenThreshold());
        existing.setYellowThreshold(updated.getYellowThreshold());
        existing.setScoringAlpha(updated.getScoringAlpha());
        existing.setMinCommitLength(updated.getMinCommitLength());
        existing.setIsolatedEventDurationSeconds(updated.getIsolatedEventDurationSeconds());

        existing.setJiraUrl(updated.getJiraUrl() != null ? updated.getJiraUrl().trim() : null);
        existing.setGithubOwner(updated.getGithubOwner() != null ? updated.getGithubOwner().trim() : null);
        existing.setJiraUsername(updated.getJiraUsername() != null ? updated.getJiraUsername().trim() : null);
        existing.setTimezone(updated.getTimezone() != null ? updated.getTimezone().trim() : null);
        existing.setBoilerplateWords(updated.getBoilerplateWords() != null ? updated.getBoilerplateWords().trim() : null);
        existing.setDoneStatuses(updated.getDoneStatuses() != null ? updated.getDoneStatuses().trim() : null);

        if (updated.getJiraUsername() != null && !updated.getJiraUsername().equals("********") && !updated.getJiraUsername().isEmpty()) {
            existing.setJiraUsername(updated.getJiraUsername().trim());
        }

        if (updated.getJiraToken() != null && !updated.getJiraToken().equals("********") && !updated.getJiraToken().isEmpty()) {
            existing.setJiraToken(updated.getJiraToken().trim());
        }
        if (updated.getGithubToken() != null && !updated.getGithubToken().equals("********") && !updated.getGithubToken().isEmpty()) {
            existing.setGithubToken(updated.getGithubToken().trim());
        }

        repository.save(existing);
        return ResponseEntity.ok("Настройки успешно сохранены");
    }

    @PostMapping("reset")
    public ResponseEntity<SystemSettings> resetSettings() {
        repository.deleteAll();

        SystemSettings resetSettings = repository.save(new SystemSettings());

        resetSettings.setJiraToken(resetSettings.getJiraToken() != null ? "********" : "");
        resetSettings.setGithubToken(resetSettings.getGithubToken() != null ? "********" : "");

        return ResponseEntity.ok(resetSettings);
    }
}