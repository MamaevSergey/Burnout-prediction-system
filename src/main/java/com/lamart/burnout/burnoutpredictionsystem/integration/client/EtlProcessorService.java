package com.lamart.burnout.burnoutpredictionsystem.integration.client;

import com.lamart.burnout.burnoutpredictionsystem.entity.Employee;
import com.lamart.burnout.burnoutpredictionsystem.entity.GitCommit;
import com.lamart.burnout.burnoutpredictionsystem.entity.GitPullRequest;
import com.lamart.burnout.burnoutpredictionsystem.entity.JiraTask;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.GithubCommitDto;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.GithubPullRequestDto;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.JiraSearchResponseDto;
import com.lamart.burnout.burnoutpredictionsystem.repository.EmployeeRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.GitCommitRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.JiraTaskRepository;
import com.lamart.burnout.burnoutpredictionsystem.util.Anonymizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.lamart.burnout.burnoutpredictionsystem.repository.GitPullRequestRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class EtlProcessorService {

    private final GithubApiClient githubApiClient;
    private final JiraApiClient jiraApiClient;
    private final GitCommitRepository gitCommitRepository;
    private final JiraTaskRepository jiraTaskRepository;
    private final EmployeeRepository employeeRepository;
    private final GitPullRequestRepository gitPullRequestRepository;

    @Transactional
    public void syncGithubCommits() {
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        List<GithubCommitDto> rawCommits = githubApiClient.fetchRecentCommits(yesterday);

        int savedCount = 0;

        for (GithubCommitDto dto : rawCommits) {
            if (dto.getAuthor() == null || dto.getAuthor().getLogin() == null) continue;

            String githubLogin = dto.getAuthor().getLogin();

            Optional<Employee> employeeOpt = employeeRepository.findByGithubUsername(githubLogin);

            if (employeeOpt.isPresent()) {
                GitCommit commit = new GitCommit();
                commit.setExternalHash(dto.getSha());
                commit.setEmployee(employeeOpt.get());
                commit.setCommittedAt(dto.getCommit().getCommitter().getDate());
                commit.setMessageLength(dto.getCommit().getMessage() != null ? dto.getCommit().getMessage().length() : 0);

                try {
                    gitCommitRepository.save(commit);
                    savedCount++;
                } catch (Exception e) {
                    log.trace("Коммит {} уже существует", dto.getSha());
                }
            } else {
                log.debug("Пропущен коммит от неизвестного пользователя: {}", githubLogin);
            }
        }
        log.info("Синхронизация GitHub завершена. Сохранено {} новых коммитов.", savedCount);
    }

    @Transactional
    public void syncJiraTasks() {
        JiraSearchResponseDto response = jiraApiClient.fetchRecentTasks(24);

        if (response == null || response.getIssues() == null) {
            log.warn("Jira API вернул пустой ответ");
            return;
        }

        int savedCount = 0;

        for (JiraSearchResponseDto.JiraIssueDto issue : response.getIssues()) {
            if (issue.getFields() == null || issue.getFields().getAssignee() == null) continue;

            String email = issue.getFields().getAssignee().getEmailAddress();

            if (email == null || email.isEmpty()) continue;

            UUID employeeId = Anonymizer.hashToUuid(email);
            Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);

            if (employeeOpt.isPresent()) {
                JiraTask task = new JiraTask();
                task.setExternalId(issue.getKey());
                task.setEmployee(employeeOpt.get());
                task.setStatus(issue.getFields().getStatus() != null ? issue.getFields().getStatus().getName() : "Unknow");

                try {
                    jiraTaskRepository.save(task);
                    savedCount++;
                } catch (Exception exception) {
                    log.trace("Задача {} уже существует", issue.getKey());
                }
            } else {
                log.debug("Пропущена задача {} от неизвестного email: {}", issue.getKey(), email);
            }
        }
        log.info("Синхронизация Jira завершена. Сохранено/обновлено {} задач.", savedCount);
    }

    @Transactional
    public void syncGithubPullRequests() {
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        List<GithubPullRequestDto> rawPrs = githubApiClient.fetchRecentPullRequests(yesterday);

        int savedCount = 0;

        for (GithubPullRequestDto dto : rawPrs) {
            if (dto.getUser() == null || dto.getUser().getLogin() == null || dto.getMergedAt() == null) continue;

            String githubLogin = dto.getUser().getLogin();
            Optional<Employee> employeeOpt = employeeRepository.findByGithubUsername(githubLogin);

            if (employeeOpt.isPresent()) {
                GitPullRequest pr = new GitPullRequest();
                pr.setExternalId(String.valueOf(dto.getNumber()));
                pr.setEmployee(employeeOpt.get());
                pr.setCreatedAt(dto.getCreatedAt());
                pr.setMergedAt(dto.getMergedAt());

                long leadTimeMins = ChronoUnit.MINUTES.between(dto.getCreatedAt(), dto.getMergedAt());
                pr.setLeadTimeMinutes((int) leadTimeMins);

                try {
                    gitPullRequestRepository.save(pr);
                    savedCount++;
                } catch (Exception exception) {
                    log.trace("Pull Request {} уже существует", dto.getNumber());
                }
            }
        }
        log.info("Синхронизация GitHub Pull Request завершена. Сохранено {} новых Pull Request", savedCount);
    }
}