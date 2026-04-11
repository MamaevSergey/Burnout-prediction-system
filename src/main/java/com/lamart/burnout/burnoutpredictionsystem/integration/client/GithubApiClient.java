package com.lamart.burnout.burnoutpredictionsystem.integration.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.GithubCommitDto;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.GithubPullRequestDto;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GithubApiClient {
    private final RestClient restClient;
    private final String repoOwner;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitHubRepoDto {
        private String name;
    }

    public GithubApiClient(
            @Value("${integration.github.token}") String token,
            @Value("${integration.github.owner}") String repoOwner) {

        this.repoOwner = repoOwner;

        this.restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .build();
    }

    public List<String> fetchAllRepositories() {
        String uri = String.format("/orgs/%s/repos?per_page=100", repoOwner);

        try {
            log.info("Запрашиваем список репозиториев для организации: {}", repoOwner);
            List<GitHubRepoDto> repos = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (repos != null) {
                return repos.stream().map(GitHubRepoDto::getName).collect(Collectors.toList());
            }
        } catch (Exception exception) {
            log.error("Ошибка при получении списка репозиториев: {}", exception.getMessage());
        }
        return Collections.emptyList();
    }

    public List<GithubCommitDto> fetchRecentCommits(String repoName, LocalDateTime since) {
        String uri = String.format("/repos/%s/%s/commits?since=%s&per_page=100",
                repoOwner, repoName, since.format(DateTimeFormatter.ISO_DATE_TIME));

        try {
            log.info("Запрашиваем коммиты из GitHub: {}", uri);
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("Ошибка при получении коммитов из GitHub: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<GithubPullRequestDto> fetchRecentPullRequests(String repoName, LocalDateTime updatedAfter) {
        String uri = String.format("/repos/%s/%s/pulls?state=closed&sort=updated&direction=desc&per_page=100",
                repoOwner, repoName);

        try {
            log.info("Запрашиваем Pull Requests из GitHub: {}", uri);
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception exception) {
            log.warn("Ошибка получения Pull Requests из GitHub: {}", exception.getMessage());
            return Collections.emptyList();
        }
    }
}
