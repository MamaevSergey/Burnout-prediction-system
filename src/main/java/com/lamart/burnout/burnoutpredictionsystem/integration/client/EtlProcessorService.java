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

        Map<String, Employee> employeeCache = employeeRepository.findAll().stream()
                .filter(e -> e.getGithubUsername() != null && !e.getGithubUsername().isEmpty())
                .collect(Collectors.toMap(Employee::getGithubUsername, e -> e));

        for (String repoName : repositories) {
            try { Thread.sleep(600); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            List<GithubCommitDto> rawCommits = githubApiClient.fetchCommitsForDate(repoName, targetDate);
            if (rawCommits.isEmpty()) continue;

            List<String> apiHashes = rawCommits.stream().map(GithubCommitDto::getSha).toList();
            Set<String> existingHashes = gitCommitRepository.findAllByExternalHashIn(apiHashes)
                    .stream().map(GitCommit::getExternalHash).collect(Collectors.toSet());

            List<GitCommit> commitsToSave = new ArrayList<>();
            for (GithubCommitDto dto : rawCommits) {
                if (dto.getAuthor() == null || dto.getAuthor().getLogin() == null) continue;

                String githubLogin = dto.getAuthor().getLogin();

                Employee employee = employeeCache.get(githubLogin);

                if (employee != null && !existingHashes.contains(dto.getSha())) {
                    GitCommit commit = new GitCommit();
                    commit.setExternalHash(dto.getSha());
                    commit.setEmployee(employee);

                    String message = dto.getCommit().getMessage();
                    commit.setMessage(message);
                    commit.setMessageLength(message != null ? message.length() : 0);

                    if (dto.getCommit().getCommitter() != null && dto.getCommit().getCommitter().getDate() != null) {
                        commit.setCommittedAt(dto.getCommit().getCommitter().getDate().toLocalDateTime());
                    } else if (dto.getCommit().getAuthor() != null && dto.getCommit().getAuthor().getDate() != null) {
                        commit.setCommittedAt(dto.getCommit().getAuthor().getDate().toLocalDateTime());
                    } else {
                        commit.setCommittedAt(targetDate.atStartOfDay());
                    }
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

        Map<String, Employee> employeeCache = employeeRepository.findAll().stream()
                .filter(e -> e.getGithubUsername() != null && !e.getGithubUsername().isEmpty())
                .collect(Collectors.toMap(Employee::getGithubUsername, e -> e));

        for (String repoName : repositories) {
            try { Thread.sleep(600); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            List<GithubPullRequestDto> rawPR = githubApiClient.fetchRecentPullRequests(repoName, targetDate.atStartOfDay());
            if (rawPR.isEmpty()) continue;

            List<String> apiIds = rawPR.stream().map(pr -> String.valueOf(pr.getNumber())).toList();
            Set<String> existingIds = gitPullRequestRepository.findAllByExternalIdIn(apiIds)
                    .stream().map(GitPullRequest::getExternalId).collect(Collectors.toSet());

            List<GitPullRequest> prsToSave = new ArrayList<>();

            for (GithubPullRequestDto dto : rawPR) {
                if (dto.getUser() == null || dto.getUser().getLogin() == null || dto.getMergedAt() == null) continue;

                boolean createdToday = dto.getCreatedAt() != null && dto.getCreatedAt().toLocalDate().equals(targetDate);
                boolean mergedToday = dto.getMergedAt() != null && dto.getMergedAt().toLocalDate().equals(targetDate);

                if (!createdToday && !mergedToday) continue;

                String githubLogin = dto.getUser().getLogin();
                Employee employee = employeeCache.get(githubLogin);
                String externalId = String.valueOf(dto.getNumber());

                if (employee != null && !existingIds.contains(externalId)) {
                    GitPullRequest pr = new GitPullRequest();
                    pr.setExternalId(externalId);
                    pr.setEmployee(employee);

                    if (dto.getCreatedAt() != null) pr.setCreatedAt(dto.getCreatedAt().toLocalDateTime());
                    if (dto.getMergedAt() != null) pr.setMergedAt(dto.getMergedAt().toLocalDateTime());

                    if (dto.getCreatedAt() != null && dto.getMergedAt() != null) {
                        long leadTimeMins = ChronoUnit.MINUTES.between(dto.getCreatedAt(), dto.getMergedAt());
                        pr.setLeadTimeMinutes((int) leadTimeMins);
                    } else {
                        pr.setLeadTimeMinutes(0);
                    }

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

        Map<UUID, Employee> employeeCache = employeeRepository.findAll().stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
        Set<Employee> employeesWithUpdatedTimezones = new HashSet<>();

        int savedCount = 0;
        for (JiraSearchResponseDto.JiraIssueDto issue : response.getIssues()) {
            try {
                if (processSingleJiraIssue(issue, employeeCache, employeesWithUpdatedTimezones)) {
                    savedCount++;
                }
            } catch (Exception exception) {
                log.error("Сбой при обработке задачи Jira {}: {}", issue.getKey(), exception.getMessage());
            }
        }

        if (!employeesWithUpdatedTimezones.isEmpty()) {
            employeeRepository.saveAll(employeesWithUpdatedTimezones);
            log.info("Массово обновлены часовые пояса для {} сотрудников.", employeesWithUpdatedTimezones.size());
        }

        log.info("Синхронизация Jira завершена. Сохранено/обновлено {} задач.", savedCount);
    }

    private boolean processSingleJiraIssue(JiraSearchResponseDto.JiraIssueDto issue,
                                           Map<UUID, Employee> employeeCache,
                                           Set<Employee> updatedEmployees) {
        if (issue.getFields() == null || issue.getFields().getAssignee() == null) return false;

        String email = issue.getFields().getAssignee().getEmailAddress();
        if (email == null || email.isEmpty()) return false;

        UUID employeeId = anonymizer.hashToUuid(email);
        Employee employee = employeeCache.get(employeeId);

        if (employee == null) {
            log.debug("Пропущена задача {} от неизвестного email: {}", issue.getKey(), email);
            return false;
        }

        String jiraTimezone = issue.getFields().getAssignee().getTimeZone();

        if (jiraTimezone != null && !jiraTimezone.equals(employee.getTimezone())) {
            employee.setTimezone(jiraTimezone);
            updatedEmployees.add(employee);
            log.info("Обновлен часовой пояс для сотрудника: {}: {}", employee.getId(), jiraTimezone);
        }

        JiraTask task = saveOrUpdateTask(issue, employee);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

        processCommentsAndAttachments(issue, task, formatter, employeeCache);
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

    private void processCommentsAndAttachments(JiraSearchResponseDto.JiraIssueDto issue, JiraTask task,
                                               DateTimeFormatter formatter, Map<UUID, Employee> employeeCache) {
        // Комментарии
        if (issue.getFields().getComment() != null && issue.getFields().getComment().getComments() != null) {
            for (JiraSearchResponseDto.CommentDto commentDto : issue.getFields().getComment().getComments()) {
                if (commentDto.getAuthor() == null || commentDto.getAuthor().getEmailAddress() == null) continue;

                try {
                    LocalDateTime commDate = ZonedDateTime.parse(commentDto.getCreated(), formatter).toLocalDateTime();
                    UUID authorId = anonymizer.hashToUuid(commentDto.getAuthor().getEmailAddress());

                    Employee author = employeeCache.get(authorId);

                    if (author != null && !jiraTaskCommentRepository.existsByTaskIdAndEmployeeIdAndCreatedAt(task.getInternalId(), authorId, commDate)) {
                        JiraTaskComment comment = new JiraTaskComment();
                        comment.setTask(task);
                        comment.setEmployee(author);
                        comment.setBodyLength(commentDto.getBody() != null ? commentDto.getBody().length() : 0);
                        comment.setAttachmentsCount(0);
                        comment.setCreatedAt(commDate);
                        jiraTaskCommentRepository.save(comment);
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

                    Employee author = employeeCache.get(authorId);

                    if (author != null && !jiraTaskCommentRepository.existsByTaskIdAndEmployeeIdAndCreatedAt(task.getInternalId(), authorId, attachDate)) {
                        JiraTaskComment mockComment = new JiraTaskComment();
                        mockComment.setTask(task);
                        mockComment.setEmployee(author);
                        mockComment.setBodyLength(0);
                        mockComment.setAttachmentsCount(1);
                        mockComment.setCreatedAt(attachDate);
                        jiraTaskCommentRepository.save(mockComment);
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