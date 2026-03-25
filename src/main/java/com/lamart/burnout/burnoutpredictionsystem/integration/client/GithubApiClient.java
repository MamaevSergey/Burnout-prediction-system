package com.lamart.burnout.burnoutpredictionsystem.integration.client;

import com.lamart.burnout.burnoutpredictionsystem.integration.dto.GithubCommitDto;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.GithubPullRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class GithubApiClient {
    private final RestClient restClient;
    private final String repoOwner;
    private final String repoName;

    public GithubApiClient(
            @Value("${integration.github.token}") String token,
            @Value("${integration.github.owner}") String repoOwner,
            @Value("${integration.github.repo}") String repoName) {

        this.repoOwner = repoOwner;
        this.repoName = repoName;

        this.restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .build();
    }

    public List<GithubCommitDto> fetchRecentCommits(LocalDateTime since) {
        String uri = String.format("/repos/%s/%s/commits?since=%s&per_page=100",
                repoOwner, repoName, since.format(DateTimeFormatter.ISO_DATE_TIME));

        try {
            log.info("Запрашиваем коммиты из GitHub: {}", uri);
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.error("Ошибка при получении коммитов из GitHub: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<GithubPullRequestDto> fetchRecentPullRequests(LocalDateTime updatedAfter) {
        String uri = String.format("/repos/%s/%s/pulls?state=closed&sort=updated&direction=desc&per_page=100", repoOwner, repoName);

        try {
            log.info("Запрашиваем Pull Requests из GitHub: {}", uri);
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception exception) {
            log.error("Ошибка получения Pull Requests из GitHub: {}", exception.getMessage());
            return Collections.emptyList();
        }
    }
}
