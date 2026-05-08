package com.lamart.burnout.burnoutpredictionsystem.integration.client;

import com.lamart.burnout.burnoutpredictionsystem.entity.SystemSettings;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.JiraSearchResponseDto;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.JiraUserDto;
import com.lamart.burnout.burnoutpredictionsystem.repository.SystemSettingsRepository;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JiraApiClient {
    private final SystemSettingsRepository settingsRepository;

    public RestClient getClient() {
        SystemSettings settings = settingsRepository.findById(1L).orElseThrow(() -> new IllegalStateException("Настройки системы не найдены"));
        String authString = settings.getJiraUsername() + ":" + settings.getJiraToken();
        String authHeaderValue = "Basic " + Base64.getEncoder().encodeToString(authString.getBytes());

        return RestClient.builder()
                .baseUrl(settings.getJiraUrl())
                .defaultHeader("Authorization", authHeaderValue)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @RateLimiter(name = "jira")
    public JiraSearchResponseDto fetchTasksForDate(LocalDate targetDate) {
        String startOfDay = targetDate.toString() + " 00:00";
        String endOfDay = targetDate + " 23:59";
        String jql = "updated >= \"" + startOfDay + "\" AND updated <= \"" + endOfDay + "\"";

        String uri = UriComponentsBuilder
                .fromPath("/rest/api/3/search/jql")
                .queryParam("jql", jql)
                .queryParam("expand", "changelog")
                .queryParam("fields", "status,assignee,created,updated,comment,attachment")
                .queryParam("maxResults", 1000)
                .build(false)
                .toUriString();

        log.info(uri);

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Отправка запроса в Jira за дату {} (Попытка {}/{})...", targetDate, attempt, maxRetries);
                return getClient().get()
                        .uri(uri)
                        .retrieve()
                        .body(JiraSearchResponseDto.class);

            } catch (Exception exception) {
                log.warn("Ошибка при подключении к API Jira (Попытка {}/{}): {}", attempt, maxRetries, exception.getMessage());

                if (attempt == maxRetries) {
                    log.error("Исчерпаны все попытки подключения к Jira.");
                    return new JiraSearchResponseDto();
                }

                try {
                    Thread.sleep(2000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return new JiraSearchResponseDto();
    }

    public List<JiraUserDto> fetchAllJiraUsers() {
        String uri = UriComponentsBuilder
                .fromPath("/rest/api/3/users/search")
                .queryParam("maxResults", 1000)
                .build(false)
                .toUriString();

        log.info("Запрос списка всех пользователей Jira...");

        try {
            JiraUserDto[] users = getClient().get()
                    .uri(uri)
                    .retrieve()
                    .body(JiraUserDto[].class);

            if (users != null) {
                return Arrays.asList(users);
            }
        } catch (Exception exception) {
            log.warn("Jira API отклонил запрос пользователей (возможно, нет прав 403 Forbidden): {}", exception.getMessage());
            return java.util.Collections.emptyList();
        }
        return Collections.emptyList();
    }
}
