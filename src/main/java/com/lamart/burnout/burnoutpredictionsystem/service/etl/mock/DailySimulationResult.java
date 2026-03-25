package com.lamart.burnout.burnoutpredictionsystem.service.etl.mock;

import com.lamart.burnout.burnoutpredictionsystem.entity.DailyMetric;
import com.lamart.burnout.burnoutpredictionsystem.entity.GitCommit;
import com.lamart.burnout.burnoutpredictionsystem.entity.GitPullRequest;
import com.lamart.burnout.burnoutpredictionsystem.entity.JiraTask;

import java.util.List;

public record DailySimulationResult(
        List<GitCommit> commits,
        GitPullRequest pullRequest,
        JiraTask jiraTask,
        DailyMetric dailyMetric
) {}