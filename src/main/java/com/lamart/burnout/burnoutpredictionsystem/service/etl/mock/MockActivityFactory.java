package com.lamart.burnout.burnoutpredictionsystem.service.etl.mock;

import com.lamart.burnout.burnoutpredictionsystem.entity.*;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Component
public class MockActivityFactory {
    private final Random random = new Random();

    public DailySimulationResult generateDailyActivity(EmployeeProfile profile, Project project, LocalDate date, boolean isWeekend, int taskSeq) {
        Employee emp = profile.employee();
        List<GitCommit> commits = new ArrayList<>();

        LocalDateTime shiftStart = date.atTime(9 + random.nextInt(3), random.nextInt(60));
        int workHours = profile.type() == ProfileType.EE_EXHAUSTED ? 10 + random.nextInt(4) : 8;
        LocalDateTime shiftEnd = shiftStart.plusHours(workHours);

        int nightWorkSeconds = 0;
        int commitCount = 2 + random.nextInt(6);
        int totalMsgLen = 0;

        for (int i = 0; i < commitCount; i++) {
            GitCommit commit = new GitCommit();
            commit.setExternalHash(UUID.randomUUID().toString().replace("-", ""));
            commit.setEmployee(emp);

            LocalDateTime commitTime;
            if (profile.type() == ProfileType.EE_EXHAUSTED && random.nextDouble() > 0.5) {
                commitTime = shiftEnd.plusHours(1).plusMinutes(random.nextInt(120));
                nightWorkSeconds += 1800;
            } else {
                commitTime = shiftStart.plusMinutes(random.nextInt(workHours * 60));
            }

            commit.setCommittedAt(commitTime);
            int msgLen = profile.type() == ProfileType.DP_CYNICAL ? 5 + random.nextInt(15) : 30 + random.nextInt(70);
            commit.setMessageLength(msgLen);
            totalMsgLen += msgLen;

            commits.add(commit);
        }

        GitPullRequest pr = new GitPullRequest();
        pr.setExternalId("PR-" + UUID.randomUUID().toString().substring(0, 8));
        pr.setEmployee(emp);
        pr.setCreatedAt(shiftStart.plusMinutes(random.nextInt(60)));
        int leadTimeMins = profile.type() == ProfileType.RPA_STAGNANT ? 180 + random.nextInt(120) : 30 + random.nextInt(60);
        pr.setMergedAt(pr.getCreatedAt().plusMinutes(leadTimeMins));
        pr.setLeadTimeMinutes(leadTimeMins);

        JiraTask task = new JiraTask();
        task.setExternalId(project.getJiraKey() + "-" + taskSeq);
        task.setEmployee(emp);
        task.setProject(project);

        if (profile.type() == ProfileType.RPA_STAGNANT) {
            task.setStatus(random.nextDouble() > 0.3 ? "В работе" : "Тестирование");
        } else {
            String[] statuses = {"Готово", "Закрыта", "Код-ревью", "Готово", "Готово", "В работе"};
            task.setStatus(statuses[random.nextInt(statuses.length)]);
        }

        int daysAgo = random.nextInt(4);
        int randomStartOffset = random.nextInt(60) - 30;
        int randomEndOffset = random.nextInt(60) - 30;

        task.setCreatedAt(shiftStart.minusDays(daysAgo).plusMinutes(randomStartOffset));
        task.setUpdatedAt(shiftEnd.plusMinutes(randomEndOffset));

        // НОВЫЙ БЛОК: Создаем физические записи комментариев
        List<JiraTaskComment> comments = new ArrayList<>();
        int commentsCount = profile.type() == ProfileType.DP_CYNICAL ? 0 : 2 + random.nextInt(5);
        for (int i = 0; i < commentsCount; i++) {
            JiraTaskComment comment = new JiraTaskComment();
            comment.setTask(task); // Привязываем к задаче
            comment.setEmployee(emp); // Привязываем к автору
            comment.setCreatedAt(shiftStart.plusMinutes(random.nextInt(workHours * 60)));
            comment.setBodyLength(10 + random.nextInt(200)); // Генерируем длину текста
            comments.add(comment);
        }

        // НОВЫЙ БЛОК: Создаем физические записи истории статусов
        List<JiraTaskChangelog> changelogs = new ArrayList<>();
        int reopenCount = profile.type() == ProfileType.RPA_STAGNANT ? 2 + random.nextInt(3) : (random.nextInt(10) > 8 ? 1 : 0);
        for (int i = 0; i < reopenCount; i++) {
            JiraTaskChangelog changelog = new JiraTaskChangelog();
            changelog.setTask(task);
            changelog.setFieldName("status");
            changelog.setFromString("Готово"); // БЫЛО "Done"
            changelog.setToString("В работе"); // БЫЛО "In Progress"
            changelog.setCreatedAt(shiftStart.plusMinutes(random.nextInt(workHours * 60)));
            changelogs.add(changelog);
        }

        // Заполняем итоговую метрику за день ---
        DailyMetric metric = new DailyMetric();
        metric.setEmployee(emp);
        metric.setDate(date);
        metric.setTotalWorkSeconds(workHours * 3600);
        metric.setNightWorkSeconds(nightWorkSeconds);
        metric.setWeekendWorkSeconds(isWeekend ? workHours * 3600 : 0);
        metric.setAvgCommitMsgLen(totalMsgLen / commitCount);
        metric.setJiraCommentsCount(commentsCount);
        metric.setReopenRate(reopenCount);
        metric.setTaskStagnationSeconds(profile.type() == ProfileType.RPA_STAGNANT ? 86400 : 0);
        metric.setPrLeadTimeAvg(leadTimeMins);

        return new DailySimulationResult(commits, pr, task, comments, changelogs, metric);
    }
}