package com.lamart.burnout.burnoutpredictionsystem.service.etl.mock;

import com.lamart.burnout.burnoutpredictionsystem.entity.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
            if (profile.type() == ProfileType.EE_EXHAUSTED && random.nextBoolean()) {
                commitTime = shiftStart.plusHours(12 + random.nextInt(5)).plusMinutes(random.nextInt(60));
                nightWorkSeconds += 3600;
            } else {
                commitTime = shiftStart.plusHours(random.nextInt(Math.max(1, workHours - 1)));
            }
            commit.setCommittedAt(commitTime);

            int msgLen = profile.type() == ProfileType.DP_CYNICAL ? 5 + random.nextInt(10) : 40 + random.nextInt(80);
            commit.setMessageLength(msgLen);
            totalMsgLen += msgLen;
            commits.add(commit);
        }

        GitPullRequest pr = new GitPullRequest();
        pr.setEmployee(emp);

        LocalDateTime prMergedAt = shiftStart.plusHours(2 + random.nextInt(6));
        pr.setMergedAt(prMergedAt);

        int leadTimeMins = profile.type() == ProfileType.RPA_STAGNANT ?
                1440 + random.nextInt(5760) :
                120 + random.nextInt(480);
        pr.setLeadTimeMinutes(leadTimeMins);
        pr.setCreatedAt(prMergedAt.minusMinutes(leadTimeMins));

        JiraTask task = new JiraTask();
        task.setExternalId(project.getJiraKey() + "-" + taskSeq);
        task.setEmployee(emp);
        task.setProject(project);
        if (profile.type() == ProfileType.RPA_STAGNANT) {
            task.setStatus(random.nextDouble() > 0.3 ? "In Progress" : "Testing");
        } else {
            String[] statuses = {"Done", "Closed", "In Review", "Done", "Done", "In Progress"};
            task.setStatus(statuses[random.nextInt(statuses.length)]);
        }

        int daysAgo = random.nextInt(4);
        int randomStartOffset = random.nextInt(60) - 30;
        int randomEndOffset = random.nextInt(60) - 30;

        task.setCreatedAt(shiftStart.minusDays(daysAgo).plusMinutes(randomStartOffset));
        task.setUpdatedAt(shiftEnd.plusMinutes(randomEndOffset));

        DailyMetric metric = new DailyMetric();
        metric.setEmployee(emp);
        metric.setDate(date);
        metric.setTotalWorkSeconds(workHours * 3600);
        metric.setNightWorkSeconds(nightWorkSeconds);
        metric.setWeekendWorkSeconds(isWeekend ? workHours * 3600 : 0);
        metric.setAvgCommitMsgLen(totalMsgLen / commitCount);
        metric.setJiraCommentsCount(profile.type() == ProfileType.DP_CYNICAL ? 0 : 2 + random.nextInt(5));
        metric.setReopenRate(profile.type() == ProfileType.RPA_STAGNANT ? 2 + random.nextInt(3) : (random.nextInt(10) > 8 ? 1 : 0));
        metric.setTaskStagnationSeconds(profile.type() == ProfileType.RPA_STAGNANT ? 5 * 3600 + random.nextInt(3600) : random.nextInt(1800));
        metric.setPrLeadTimeAvg(leadTimeMins);

        return new DailySimulationResult(commits, pr, task, metric);
    }
}