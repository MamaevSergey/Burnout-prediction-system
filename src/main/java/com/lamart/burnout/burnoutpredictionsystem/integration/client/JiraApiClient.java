package com.lamart.burnout.burnoutpredictionsystem.integration.client;

import com.lamart.burnout.burnoutpredictionsystem.integration.dto.JiraSearchResponseDto;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.Base64;

@Slf4j
@Service
public class JiraApiClient {
    private final RestClient restClient;

    public JiraApiClient(
            @Value("${integration.jira.url}") String jiraUrl,
            @Value("${integration.jira.username}") String username,
            @Value("${integration.jira.token}") String token) {

        String authString = username + ":" + token;
        String authHeaderValue = "Basic " + Base64.getEncoder().encodeToString(authString.getBytes());

        this.restClient = RestClient.builder()
                .baseUrl(jiraUrl)
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
                return restClient.get()
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
}
