package com.lamart.burnout.burnoutpredictionsystem.integration.client;

import com.lamart.burnout.burnoutpredictionsystem.integration.dto.JiraSearchResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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

    public JiraSearchResponseDto fetchRecentTasks(int hoursAgo) {
        String jql = String.format("updated >= -%dh", hoursAgo);
        String uri = "/rest/api/3/search/jql?jql=" + jql + "&expand=changelog&fields=status,assignee,created,updated,comment,attachment&maxResults=1000";

        try {
            log.info("Отправка запроса в Jira...");
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(JiraSearchResponseDto.class);

        } catch (Exception e) {
            log.error("Ошибка при подключении к API Jira: {}", e.getMessage());
            return new JiraSearchResponseDto();
        }
    }
}
