package com.lamart.burnout.burnoutpredictionsystem.integration.client;

import com.lamart.burnout.burnoutpredictionsystem.entity.*;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.GithubCommitDto;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.GithubPullRequestDto;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.JiraSearchResponseDto;
import com.lamart.burnout.burnoutpredictionsystem.repository.*;
import com.lamart.burnout.burnoutpredictionsystem.util.Anonymizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private final ProjectRepository projectRepository;
    private final JiraTaskCommentRepository jiraTaskCommentRepository;
    private final JiraTaskChangelogRepository jiraTaskChangelogRepository;

    @Transactional
    public void syncGithubCommits() {
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        List<String> repositories = githubApiClient.fetchAllRepositories();

        int savedCount = 0;
        for (String repoName : repositories) {
            List<GithubCommitDto> rawCommits = githubApiClient.fetchRecentCommits(repoName, yesterday);

            for (GithubCommitDto dto : rawCommits) {
                if (dto.getAuthor() == null || dto.getAuthor().getLogin() == null) continue;

                String githubLogin = dto.getAuthor().getLogin();

                Optional<Employee> employeeOpt = employeeRepository.findByGithubUsername(githubLogin);

                if (employeeOpt.isPresent()) {

                    if (gitCommitRepository.existsByExternalHash(dto.getSha())) {
                        log.trace("Коммит {} уже существует. Пропускаем...", dto.getSha());
                        continue;
                    }

                    GitCommit commit = new GitCommit();
                    commit.setExternalHash(dto.getSha());
                    commit.setEmployee(employeeOpt.get());
                    commit.setCommittedAt(dto.getCommit().getCommitter().getDate());
                    commit.setMessageLength(dto.getCommit().getMessage() != null ? dto.getCommit().getMessage().length() : 0);

                    gitCommitRepository.save(commit);
                    savedCount++;
                } else {
                    log.debug("Пропущен коммит от неизвестного пользователя: {}", githubLogin);
                }
            }
        }
        log.info("Синхронизация GitHub завершена. Сохранено {} новых коммитов.", savedCount);
    }

    @Transactional
    public void syncJiraTasks() {
        JiraSearchResponseDto response = jiraApiClient.fetchRecentTasks(24);

        if (response == null || response.getIssues() == null) {
            log.warn("Jira API вернул пустой ответ.");
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
                JiraTask task = jiraTaskRepository.findByExternalId(issue.getKey())
                        .orElseGet(JiraTask::new);
                task.setExternalId(issue.getKey());
                task.setEmployee(employeeOpt.get());
                task.setStatus(issue.getFields().getStatus() != null ? issue.getFields().getStatus().getName() : "Unknown");

                String projectKey = issue.getKey().contains("-") ? issue.getKey().split("-")[0] : null;
                if (projectKey != null) {
                    Project project = projectRepository.findByJiraKey(projectKey)
                            .orElseGet(() -> {
                                Project newProj = new Project();
                                newProj.setJiraKey(projectKey);
                                newProj.setName(projectKey);
                                return projectRepository.save(newProj);
                            });
                    task.setProject(project);
                }

                DateTimeFormatter jiraFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
                try {
                    if (issue.getFields().getCreated() != null) {
                        task.setCreatedAt(ZonedDateTime.parse(issue.getFields().getCreated(), jiraFormatter).toLocalDateTime());
                    }
                    if (issue.getFields().getUpdated() != null) {
                        task.setUpdatedAt(ZonedDateTime.parse(issue.getFields().getUpdated(), jiraFormatter).toLocalDateTime());
                    }
                } catch (Exception e) {
                    log.warn("Не удалось распарсить даты для задачи {}: {}", issue.getKey(), e.getMessage());
                }

                jiraTaskRepository.save(task);

                // Статусы и комментарии
                if (issue.getFields().getComment() != null && issue.getFields().getComment().getComments() != null) {
                    for (JiraSearchResponseDto.CommentDto commentDto : issue.getFields().getComment().getComments()) {
                        if (commentDto.getAuthor() == null || commentDto.getAuthor().getEmailAddress() == null) continue;

                        LocalDateTime commDate;
                        try {
                            commDate = ZonedDateTime.parse(commentDto.getCreated(), jiraFormatter).toLocalDateTime();
                        } catch (Exception exception) {
                            log.warn("Не удалось распарсить дату комментария: {}. Пропускаем.", commentDto.getCreated());
                            continue; // Без LocalDateTime.now(), просто пропуск.
                        }

                        UUID authorId = Anonymizer.hashToUuid(commentDto.getAuthor().getEmailAddress());
                        Optional<Employee> commentAuthorOpt = employeeRepository.findById(authorId);

                        if (commentAuthorOpt.isPresent()) {
                            // Усиленная дедупликация
                            if (jiraTaskCommentRepository.existsByTaskIdAndEmployeeIdAndCreatedAt(task.getInternalId(), authorId, commDate)) {
                                continue;
                            }

                            JiraTaskComment comment = new JiraTaskComment();
                            comment.setTask(task);
                            comment.setEmployee(commentAuthorOpt.get());
                            comment.setBodyLength(commentDto.getBody() != null ? commentDto.getBody().length() : 0);
                            comment.setCreatedAt(commDate);

                            jiraTaskCommentRepository.save(comment);
                        }
                    }
                }

                // Вложения (считаем как комментарии)
                if (issue.getFields().getAttachment() != null) {
                    for (JiraSearchResponseDto.AttachmentDto attachDto : issue.getFields().getAttachment()) {
                        if (attachDto.getAuthor() == null || attachDto.getAuthor().getEmailAddress() == null) continue;

                        LocalDateTime attachDate;
                        try {
                            attachDate = ZonedDateTime.parse(attachDto.getCreated(), jiraFormatter).toLocalDateTime();
                        } catch (Exception exception) {
                            log.warn("Не удалось распарсить дату вложения: {}. Пропускаем.", attachDto.getCreated());
                            continue;
                        }

                        UUID authorId = Anonymizer.hashToUuid(attachDto.getAuthor().getEmailAddress());
                        Optional<Employee> authorOpt = employeeRepository.findById(authorId);

                        if (authorOpt.isPresent()) {
                            if (jiraTaskCommentRepository.existsByTaskIdAndEmployeeIdAndCreatedAt(task.getInternalId(), authorId, attachDate)) {
                                continue;
                            }

                            JiraTaskComment mockComment = new JiraTaskComment();
                            mockComment.setTask(task);
                            mockComment.setEmployee(authorOpt.get());
                            mockComment.setBodyLength(0);
                            mockComment.setCreatedAt(attachDate);
                            jiraTaskCommentRepository.save(mockComment);
                        }
                    }
                }

                // История статусов (Changelog)
                if (issue.getChangelog() != null && issue.getChangelog().getHistories() != null) {
                    for (JiraSearchResponseDto.HistoryDto historyDto : issue.getChangelog().getHistories()) {

                        LocalDateTime changelogDate;

                        try {
                            changelogDate = ZonedDateTime.parse(historyDto.getCreated(), jiraFormatter).toLocalDateTime();
                        } catch (Exception exception) {
                            log.warn("Не удалось распарсить дату changelog: {}. Пропускаем.", historyDto.getCreated());
                            continue;
                        }

                        if (historyDto.getItems() == null) continue;

                        for (JiraSearchResponseDto.HistoryItemDto itemDto : historyDto.getItems()) {
                            if ("status".equalsIgnoreCase(itemDto.getField())) {

                                if (jiraTaskChangelogRepository.existsByTaskIdAndFieldNameAndCreatedAt(
                                        task.getInternalId(), itemDto.getField(), changelogDate)) {
                                    continue;
                                }

                                JiraTaskChangelog changelog = new JiraTaskChangelog();
                                changelog.setTask(task);
                                changelog.setFieldName(itemDto.getField());
                                changelog.setFromString(itemDto.getFromString());
                                changelog.setToString(itemDto.getToString());
                                changelog.setCreatedAt(changelogDate);

                                jiraTaskChangelogRepository.save(changelog);
                            }
                        }
                    }
                }
                savedCount++;
            } else {
                log.debug("Пропущена задача {} от неизвестного email: {}", issue.getKey(), email);
            }
        }
        log.info("Синхронизация Jira завершена. Сохранено/обновлено {} задач.", savedCount);
    }

    @Transactional
    public void syncGithubPullRequests() {
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        List<String> repositories = githubApiClient.fetchAllRepositories();

        int savedCount = 0;

        for (String repoName : repositories) {
            List<GithubPullRequestDto> rawPR = githubApiClient.fetchRecentPullRequests(repoName, yesterday);

            for (GithubPullRequestDto dto : rawPR) {
                if (dto.getUser() == null || dto.getUser().getLogin() == null || dto.getMergedAt() == null) continue;

                String githubLogin = dto.getUser().getLogin();
                Optional<Employee> employeeOpt = employeeRepository.findByGithubUsername(githubLogin);

                if (employeeOpt.isPresent()) {
                    String externalId = String.valueOf(dto.getNumber());

                    if (gitPullRequestRepository.existsByExternalId(externalId)) {
                        log.trace("Pull Request {} уже существует. Пропускаем...", externalId);
                        continue;
                    }

                    GitPullRequest pr = new GitPullRequest();
                    pr.setExternalId(externalId);
                    pr.setEmployee(employeeOpt.get());
                    pr.setCreatedAt(dto.getCreatedAt());
                    pr.setMergedAt(dto.getMergedAt());

                    long leadTimeMins = ChronoUnit.MINUTES.between(dto.getCreatedAt(), dto.getMergedAt());
                    pr.setLeadTimeMinutes((int) leadTimeMins);

                    gitPullRequestRepository.save(pr);
                    savedCount++;
                }
            }
        }
        log.info("Синхронизация GitHub Pull Request завершена. Сохранено {} новых Pull Request", savedCount);
    }
}