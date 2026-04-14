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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

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
    private final Anonymizer anonymizer;

    public void syncGithubCommits(LocalDate targetDate) {
        List<String> repositories = githubApiClient.fetchAllRepositories();
        int savedCount = 0;

        for (String repoName : repositories) {
            List<GithubCommitDto> rawCommits = githubApiClient.fetchCommitsForDate(repoName, targetDate);

            if (rawCommits.isEmpty()) continue;

            List<String> apiHashes = rawCommits.stream().map(GithubCommitDto::getSha).toList();
            Set<String> existingHashes = gitCommitRepository.findAllByExternalHashIn(apiHashes)
                    .stream().map(GitCommit::getExternalHash).collect(Collectors.toSet());

            List<GitCommit> commitsToSave = new ArrayList<>();
            for (GithubCommitDto dto : rawCommits) {
                if (dto.getAuthor() == null || dto.getAuthor().getLogin() == null) continue;

                String githubLogin = dto.getAuthor().getLogin();
                Optional<Employee> employeeOpt = employeeRepository.findByGithubUsername(githubLogin);

                if (employeeOpt.isPresent() && !existingHashes.contains(dto.getSha())) {
                    GitCommit commit = new GitCommit();
                    commit.setExternalHash(dto.getSha());
                    commit.setEmployee(employeeOpt.get());
                    commit.setCommittedAt(dto.getCommit().getCommitter().getDate());
                    commit.setMessageLength(dto.getCommit().getMessage() != null ? dto.getCommit().getMessage().length() : 0);

                    commitsToSave.add(commit);
                }
            }

            if (!commitsToSave.isEmpty()) {
                gitCommitRepository.saveAll(commitsToSave);
                savedCount += commitsToSave.size();
            }
        }
        log.info("Синхронизация GitHub Commits завершена. Сохранено {} новых коммитов.", savedCount);
    }

    public void syncGithubPullRequests(LocalDate targetDate) {
        List<String> repositories = githubApiClient.fetchAllRepositories();
        int savedCount = 0;

        for (String repoName : repositories) {
            List<GithubPullRequestDto> rawPR = githubApiClient.fetchRecentPullRequests(repoName, targetDate.atStartOfDay());

            if (rawPR.isEmpty()) continue;

            List<String> apiIds = rawPR.stream().map(pr -> String.valueOf(pr.getNumber())).toList();
            Set<String> existingIds = gitPullRequestRepository.findAllByExternalIdIn(apiIds)
                    .stream().map(GitPullRequest::getExternalId).collect(Collectors.toSet());

            List<GitPullRequest> prsToSave = new ArrayList<>();

            for (GithubPullRequestDto dto : rawPR) {
                if (dto.getUser() == null || dto.getUser().getLogin() == null || dto.getMergedAt() == null) continue;

                if (!dto.getMergedAt().toLocalDate().equals(targetDate)) continue;

                String githubLogin = dto.getUser().getLogin();
                Optional<Employee> employeeOpt = employeeRepository.findByGithubUsername(githubLogin);
                String externalId = String.valueOf(dto.getNumber());

                if (employeeOpt.isPresent() && !existingIds.contains(externalId)) {
                    GitPullRequest pr = new GitPullRequest();
                    pr.setExternalId(externalId);
                    pr.setEmployee(employeeOpt.get());
                    pr.setCreatedAt(dto.getCreatedAt());
                    pr.setMergedAt(dto.getMergedAt());

                    long leadTimeMins = ChronoUnit.MINUTES.between(dto.getCreatedAt(), dto.getMergedAt());
                    pr.setLeadTimeMinutes((int) leadTimeMins);

                    prsToSave.add(pr);
                }
            }

            if (!prsToSave.isEmpty()) {
                gitPullRequestRepository.saveAll(prsToSave);
                savedCount += prsToSave.size();
            }
        }
        log.info("Синхронизация GitHub Pull Request завершена. Сохранено {} новых Pull Request.", savedCount);
    }

    public void syncJiraTasks(LocalDate targetDate) {
        JiraSearchResponseDto response = jiraApiClient.fetchTasksForDate(targetDate);

        if (response == null || response.getIssues() == null) {
            log.warn("Jira API вернул пустой ответ.");
            return;
        }

        int savedCount = 0;
        for (JiraSearchResponseDto.JiraIssueDto issue : response.getIssues()) {
            try {
                if (processSingleJiraIssue(issue)) {
                    savedCount++;
                }
            } catch (Exception exception) {
                log.error("Сбой при обработке задачи Jira {}: {}", issue.getKey(), exception.getMessage());
            }
        }
        log.info("Синхронизация Jira завершена. Сохранено/обновлено {} задач.", savedCount);
    }

    private boolean processSingleJiraIssue(JiraSearchResponseDto.JiraIssueDto issue) {
        if (issue.getFields() == null || issue.getFields().getAssignee() == null) return false;

        String email = issue.getFields().getAssignee().getEmailAddress();
        if (email == null || email.isEmpty()) return false;

        UUID employeeId = anonymizer.hashToUuid(email);
        Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);

        if (employeeOpt.isEmpty()) {
            log.debug("Пропущена задача {} от неизвестного email: {}", issue.getKey(), email);
            return false;
        }

        JiraTask task = saveOrUpdateTask(issue, employeeOpt.get());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

        processCommentsAndAttachments(issue, task, formatter);
        processChangelog(issue, task, formatter);

        return true;
    }

    private JiraTask saveOrUpdateTask(JiraSearchResponseDto.JiraIssueDto issue, Employee employee) {
        JiraTask task = jiraTaskRepository.findByExternalId(issue.getKey()).orElseGet(JiraTask::new);
        task.setExternalId(issue.getKey());
        task.setEmployee(employee);
        task.setStatus(issue.getFields().getStatus() != null ? issue.getFields().getStatus().getName() : "Unknown");

        String projectKey = issue.getKey().contains("-") ? issue.getKey().split("-")[0] : null;
        if (projectKey != null) {
            Project project = projectRepository.findByJiraKey(projectKey).orElseGet(() -> {
                Project newProj = new Project();
                newProj.setJiraKey(projectKey);
                newProj.setName(projectKey);
                return projectRepository.save(newProj);
            });
            task.setProject(project);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        try {
            if (issue.getFields().getCreated() != null)
                task.setCreatedAt(ZonedDateTime.parse(issue.getFields().getCreated(), formatter).toLocalDateTime());
            if (issue.getFields().getUpdated() != null)
                task.setUpdatedAt(ZonedDateTime.parse(issue.getFields().getUpdated(), formatter).toLocalDateTime());
        } catch (Exception e) {
            log.warn("Не удалось распарсить даты для задачи {}", issue.getKey());
        }

        return jiraTaskRepository.save(task);
    }

    private void processCommentsAndAttachments(JiraSearchResponseDto.JiraIssueDto issue, JiraTask task, DateTimeFormatter formatter) {
        // Комментарии
        if (issue.getFields().getComment() != null && issue.getFields().getComment().getComments() != null) {
            for (JiraSearchResponseDto.CommentDto commentDto : issue.getFields().getComment().getComments()) {
                if (commentDto.getAuthor() == null || commentDto.getAuthor().getEmailAddress() == null) continue;

                try {
                    LocalDateTime commDate = ZonedDateTime.parse(commentDto.getCreated(), formatter).toLocalDateTime();
                    UUID authorId = anonymizer.hashToUuid(commentDto.getAuthor().getEmailAddress());

                    if (!jiraTaskCommentRepository.existsByTaskIdAndEmployeeIdAndCreatedAt(task.getInternalId(), authorId, commDate)) {
                        employeeRepository.findById(authorId).ifPresent(author -> {
                            JiraTaskComment comment = new JiraTaskComment();
                            comment.setTask(task);
                            comment.setEmployee(author);
                            comment.setBodyLength(commentDto.getBody() != null ? commentDto.getBody().length() : 0);
                            comment.setCreatedAt(commDate);
                            jiraTaskCommentRepository.save(comment);
                        });
                    }
                } catch (Exception e) {
                    log.warn("Ошибка парсинга даты комментария: {}", commentDto.getCreated());
                }
            }
        }

        // Вложения
        if (issue.getFields().getAttachment() != null) {
            for (JiraSearchResponseDto.AttachmentDto attachDto : issue.getFields().getAttachment()) {
                if (attachDto.getAuthor() == null || attachDto.getAuthor().getEmailAddress() == null) continue;

                try {
                    LocalDateTime attachDate = ZonedDateTime.parse(attachDto.getCreated(), formatter).toLocalDateTime();
                    UUID authorId = anonymizer.hashToUuid(attachDto.getAuthor().getEmailAddress());

                    if (!jiraTaskCommentRepository.existsByTaskIdAndEmployeeIdAndCreatedAt(task.getInternalId(), authorId, attachDate)) {
                        employeeRepository.findById(authorId).ifPresent(author -> {
                            JiraTaskComment mockComment = new JiraTaskComment();
                            mockComment.setTask(task);
                            mockComment.setEmployee(author);
                            mockComment.setBodyLength(0);
                            mockComment.setCreatedAt(attachDate);
                            jiraTaskCommentRepository.save(mockComment);
                        });
                    }
                } catch (Exception e) {
                    log.warn("Ошибка парсинга даты вложения: {}", attachDto.getCreated());
                }
            }
        }
    }

    private void processChangelog(JiraSearchResponseDto.JiraIssueDto issue, JiraTask task, DateTimeFormatter formatter) {
        if (issue.getChangelog() == null || issue.getChangelog().getHistories() == null) return;

        for (JiraSearchResponseDto.HistoryDto historyDto : issue.getChangelog().getHistories()) {
            if (historyDto.getItems() == null) continue;

            try {
                LocalDateTime changelogDate = ZonedDateTime.parse(historyDto.getCreated(), formatter).toLocalDateTime();

                for (JiraSearchResponseDto.HistoryItemDto itemDto : historyDto.getItems()) {
                    if ("status".equalsIgnoreCase(itemDto.getField())) {
                        if (!jiraTaskChangelogRepository.existsByTaskIdAndFieldNameAndCreatedAt(
                                task.getInternalId(), itemDto.getField(), changelogDate)) {

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
            } catch (Exception e) {
                log.warn("Ошибка парсинга даты changelog: {}", historyDto.getCreated());
            }
        }
    }
}