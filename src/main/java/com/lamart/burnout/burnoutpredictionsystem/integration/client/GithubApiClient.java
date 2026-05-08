package com.lamart.burnout.burnoutpredictionsystem.integration.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lamart.burnout.burnoutpredictionsystem.entity.SystemSettings;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.GithubCommitDto;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.GithubPullRequestDto;
import com.lamart.burnout.burnoutpredictionsystem.repository.SystemSettingsRepository;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubApiClient {
    private final SystemSettingsRepository settingsRepository;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitHubRepoDto {
        private String name;
    }

    private RestClient getClient(String token) {
        return RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .build();
    }

    @RateLimiter(name = "github")
    public List<String> fetchAllRepositories() {
        SystemSettings settings = settingsRepository.findById(1L).orElseThrow();
        String uri = String.format("/orgs/%s/repos?per_page=100", settings.getGithubOwner());

        try {
            log.info("Запрашиваем список репозиториев для организации: {}", settings.getGithubOwner());
            List<GitHubRepoDto> repos = getClient(settings.getGithubToken()).get()
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

    @RateLimiter(name = "github")
    public List<GithubCommitDto> fetchCommitsForDate(String repoName, LocalDate targetDate) {
        SystemSettings settings = settingsRepository.findById(1L).orElseThrow();
        String since = targetDate.atStartOfDay().atOffset(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        String until = targetDate.atTime(java.time.LocalTime.MAX).atOffset(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);

        List<GithubCommitDto> allCommits = new ArrayList<>();
        int page = 1;
        int perPage = 100;

        try {
            while (true) {
                String uri = String.format("/repos/%s/%s/commits?since=%s&until=%s&per_page=%d&page=%d",
                        settings.getGithubOwner(), repoName, since, until, perPage, page);

                log.info("Запрашиваем коммиты из GitHub: {} (страница {})", repoName, page);

                List<GithubCommitDto> pageCommits = getClient(settings.getGithubToken()).get()
                        .uri(uri)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});

                if (pageCommits == null || pageCommits.isEmpty()) {
                    break;
                }

                allCommits.addAll(pageCommits);

                if (pageCommits.size() < perPage) {
                    break;
                }

                page++;
            }
            return allCommits;
        } catch (Exception exception) {
            log.warn("Ошибка при получении коммитов из GitHub для {}: {}", repoName, exception.getMessage());
            throw new RuntimeException("Сбой API GitHub при запросе коммитов: " + exception.getMessage(), exception);
        }
    }

    @RateLimiter(name = "github")
    public List<GithubPullRequestDto> fetchRecentPullRequests(String repoName, LocalDateTime updatedAfter) {
        SystemSettings settings = settingsRepository.findById(1L).orElseThrow();
        List<GithubPullRequestDto> allPRs = new ArrayList<>();

        int page = 1;
        int perPage = 100;

        try {
            while (true) {
                String uri = String.format("/repos/%s/%s/pulls?state=all&sort=updated&direction=desc&per_page=%d&page=%d",
                        settings.getGithubOwner(), repoName, perPage, page);
                log.info("Отладка PR: OwnerName {} и Token: {}", settings.getGithubOwner(), settings.getGithubToken());
                log.info("Запрашиваем Pull Requests из GitHub: {} (страница {})", repoName, page);

                List<GithubPullRequestDto> pagePRs = getClient(settings.getGithubToken()).get()
                        .uri(uri)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});

                if (pagePRs == null || pagePRs.isEmpty()) {
                    break;
                }

                boolean hasOlderPRs = false;

                for (GithubPullRequestDto pr : pagePRs) {
                    if (pr.getUpdatedAt() != null && pr.getUpdatedAt().toLocalDateTime().isBefore(updatedAfter)) {
                        hasOlderPRs = true;
                    } else {
                        allPRs.add(pr);
                    }
                }

                if (hasOlderPRs || pagePRs.size() < perPage) {
                    break;
                }

                page++;
            }
            return allPRs;
        } catch (Exception exception) {
            log.warn("Ошибка получения Pull Requests из GitHub для {}: {}", repoName, exception.getMessage());
            throw new RuntimeException("Сбой API GitHub при запросе PR: " + exception.getMessage(), exception);
        }
    }
}
