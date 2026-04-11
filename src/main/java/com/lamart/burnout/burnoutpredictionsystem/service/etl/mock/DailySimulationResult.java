package com.lamart.burnout.burnoutpredictionsystem.service.etl.mock;

import com.lamart.burnout.burnoutpredictionsystem.entity.*;

import java.util.List;

public record DailySimulationResult(
        List<GitCommit> commits,
        GitPullRequest pullRequest,
        JiraTask jiraTask,
        List<JiraTaskComment> comments,
        List<JiraTaskChangelog> changelogs,
        DailyMetric dailyMetric
) {}