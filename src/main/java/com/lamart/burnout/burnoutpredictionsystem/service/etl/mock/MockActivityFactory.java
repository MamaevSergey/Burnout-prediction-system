package com.lamart.burnout.burnoutpredictionsystem.service.etl.mock;

import com.lamart.burnout.burnoutpredictionsystem.entity.*;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class MockActivityFactory {
    private static final List<String> COMMITS_FEATURE = List.of(
            "Implement user notification service",
            "Add pagination to employee list endpoint",
            "Integrate OAuth2 with Google provider",
            "Implement JWT refresh token rotation",
            "Add Redis caching layer for session data",
            "Create burnout score aggregation pipeline",
            "Implement daily metric collection job",
            "Add Swagger annotations to REST controllers",
            "Implement role-based access control",
            "Add email verification flow",
            "Integrate S3 file storage for attachments",
            "Implement webhook handler for Jira events",
            "Add GraphQL schema for analytics dashboard",
            "Implement async task processing with RabbitMQ",
            "Add health check endpoints"
    );

    private static final List<String> COMMITS_FIX = List.of(
            "Fix null pointer exception in metric aggregator",
            "Fix timezone offset in date range queries",
            "Resolve race condition in concurrent task updates",
            "Fix N+1 query problem in employee repository",
            "Correct calculation of lead time for merged PRs",
            "Fix broken pagination offset for large datasets",
            "Handle edge case when employee has no commits",
            "Fix CORS policy for mobile client origin",
            "Resolve token expiry not being refreshed correctly",
            "Fix memory leak in scheduled task executor"
    );

    private static final List<String> COMMITS_REFACTOR = List.of(
            "Refactor authentication filter chain",
            "Extract metric calculation into dedicated service",
            "Simplify DTO mapping with MapStruct",
            "Replace magic numbers with named constants",
            "Decompose god-class AnalyticsService",
            "Migrate from RestTemplate to WebClient",
            "Refactor error handling to use problem details RFC",
            "Clean up unused imports and dead code",
            "Rename variables for domain clarity",
            "Move business logic from controller to service layer"
    );

    private static final List<String> COMMITS_DOCS_TESTS = List.of(
            "Add unit tests for scoring engine",
            "Add integration tests for ETL pipeline",
            "Update README with local setup instructions",
            "Document API contracts in OpenAPI spec",
            "Add Javadoc to repository interfaces",
            "Write tests for edge cases in burnout calculator",
            "Add test fixtures for mock data generation",
            "Document environment variables in .env.example"
    );

    private static final List<String> COMMITS_DEGRADED = List.of(
            "fix", "update", "changes", "wip", "temp", ".", "!!!", "done",
            "исправил", "обновил", "хз", "тест", "asdf", "пофиксил", "правки"
    );

    private static final List<String> COMMITS_RUSHED = List.of(
            "Fix critical bug before release",
            "Hotfix for production issue",
            "Emergency patch for auth service",
            "Quick fix for broken build",
            "Urgent: resolve deployment failure",
            "Last minute changes before demo",
            "Fix issue found during code review",
            "Patch security vulnerability"
    );

    private static final List<String> JIRA_COMMENTS_DETAILED = List.of(
            "Reviewed the implementation and found a couple of edge cases that need to be handled. The current approach works for the happy path but will fail when the input list is empty. Suggest adding a guard clause at the top of the method.",
            "Blocked by the upstream team — waiting for them to expose the new endpoint. Will ping them in the standup. ETA end of week.",
            "Completed the initial implementation. Added unit tests covering the main scenarios. Found one issue with the date serialization which I fixed inline. PR is ready for review.",
            "After discussing with the team lead, we decided to go with the event-sourcing approach instead of direct DB updates. This will require refactoring the repository layer. Updating the task estimate accordingly.",
            "The performance test showed that the current query takes ~800ms on a dataset of 10k records. Added an index on the employee_id column — now running at ~40ms. Will document this in the architecture decision record.",
            "Spent most of today debugging the JWT validation issue. Turned out the clock skew between services was causing token rejection. Fixed by adding a 30-second leeway. Closing this subtask.",
            "Pair-programmed with a colleague on the caching strategy. We went with write-through cache invalidation. The implementation is clean and all existing tests still pass."
    );

    private static final List<String> JIRA_COMMENTS_SHORT = List.of(
            "Done, PR raised.",
            "Blocked, waiting for design approval.",
            "In progress, ~70% complete.",
            "Will pick this up after standup.",
            "Fixed in latest commit.",
            "Needs discussion in next sprint review.",
            "Reviewed, left comments on PR.",
            "Estimate revised to 3 SP due to discovered complexity."
    );

    private static final Random random = new Random();
    private static final java.util.concurrent.atomic.AtomicInteger TASK_ID_SEQ = new java.util.concurrent.atomic.AtomicInteger(1000);

    public DailySimulationResult generateDailyActivity(
            EmployeeProfile profile,
            Project project,
            LocalDate date,
            boolean isWeekend,
            double burnoutPhase
    ) {
        Employee emp = profile.employee();
        ProfileType type = profile.type();

        WorkDayParams day = computeWorkDay(profile, type, isWeekend, burnoutPhase);

        List<LocalDateTime> commitTimes = generateCommitTimes(day, date);
        List<LocalDateTime> jiraActivityTimes = generateJiraActivityTimes(day, date, type, burnoutPhase);

        List<GitCommit> commits = buildCommits(emp, commitTimes, type, burnoutPhase, day);

        List<GitPullRequest> prs = buildPullRequests(emp, day, date, type, burnoutPhase);

        int taskCount = computeTaskCount(type, burnoutPhase, day.productive);
        List<JiraTask> tasks = new ArrayList<>();
        List<JiraTaskComment> allComments = new ArrayList<>();
        List<JiraTaskChangelog> allChangelogs = new ArrayList<>();

        for (int t = 0; t < taskCount; t++) {
            int uniqueSeq = TASK_ID_SEQ.getAndIncrement();
            JiraTask task = buildJiraTask(emp, project, uniqueSeq, day, date, type, burnoutPhase);
            tasks.add(task);

            List<JiraTaskComment> taskComments = buildComments(emp, task, jiraActivityTimes, type, burnoutPhase);
            allComments.addAll(taskComments);

            List<JiraTaskChangelog> taskChangelogs = buildChangelogs(task, day, date, type, burnoutPhase);
            allChangelogs.addAll(taskChangelogs);
        }

        return new DailySimulationResult(commits, prs, tasks, allComments, allChangelogs);
    }

    record WorkDayParams(
            LocalDateTime shiftStart,
            LocalDateTime shiftEnd,
            int workMinutes,
            boolean productive,
            boolean hadLunch,
            boolean workedNight
    ) {}

    private WorkDayParams computeWorkDay(EmployeeProfile profile, ProfileType type, boolean isWeekend, double phase) {
        boolean earlyBird = profile.workStyleEarlyBird();
        double prod = profile.baseProductivity();

        int baseStartHour = earlyBird ? 7 + random.nextInt(2) : 10 + random.nextInt(2);
        int startMinute = random.nextInt(60);

        int baseWorkMinutes = (int) (420 + prod * 60 + (random.nextGaussian() * 20));

        boolean workedNight = false;
        boolean productive = true;

        switch (type) {
            case EE_EXHAUSTED -> {
                if (earlyBird) baseStartHour = Math.max(6, baseStartHour - 1);
                baseWorkMinutes = (int) (baseWorkMinutes + 300 + phase * 300 + random.nextInt(180));
                workedNight = random.nextDouble() < 0.6 + phase * 0.3;
            }
            case DP_CYNICAL -> {
                baseWorkMinutes = (int) (baseWorkMinutes - phase * 120);
                productive = phase < 0.5 || random.nextDouble() > phase;
            }
            case RPA_STAGNANT -> {
                baseWorkMinutes = (int) (baseWorkMinutes - phase * 60 + random.nextInt(30));
            }
            case BURNED_OUT -> {
                if (random.nextDouble() < 0.4) {
                    baseWorkMinutes = (int) (baseWorkMinutes + 240 + phase * 300);
                    workedNight = random.nextDouble() < 0.5 + phase * 0.3;
                } else {
                    baseWorkMinutes = (int) (baseWorkMinutes * (1.0 - phase * 0.5));
                    productive = random.nextDouble() > phase * 0.6;
                }
            }
            case ELEVATED_RISK -> {
                baseWorkMinutes = (int) (baseWorkMinutes + phase * 60 + random.nextInt(30));
            }
            default -> {}
        }

        baseWorkMinutes = Math.max(120, Math.min(840, baseWorkMinutes));

        LocalDateTime start = LocalDate.now().atTime(baseStartHour, startMinute);
        LocalDateTime shiftStart = LocalDateTime.of(2000, 1, 1, baseStartHour, startMinute);
        LocalDateTime shiftEnd = shiftStart.plusMinutes(baseWorkMinutes);

        boolean hadLunch = baseWorkMinutes > 300 && !(type == ProfileType.EE_EXHAUSTED && phase > 0.7 && random.nextDouble() < 0.3);

        return new WorkDayParams(shiftStart, shiftEnd, baseWorkMinutes, productive, hadLunch, workedNight);
    }

    private WorkDayParams adjustToDate(WorkDayParams base, LocalDate date) {
        LocalDateTime start = date.atTime(base.shiftStart().toLocalTime());
        LocalDateTime end = date.atTime(base.shiftEnd().toLocalTime());
        if (end.isBefore(start)) end = end.plusDays(1);
        return new WorkDayParams(start, end, base.workMinutes(), base.productive(), base.hadLunch(), base.workedNight());
    }

    private List<LocalDateTime> generateCommitTimes(WorkDayParams day, LocalDate date) {
        WorkDayParams d = adjustToDate(day, date);
        List<LocalDateTime> times = new ArrayList<>();

        int totalMinutes = d.workMinutes();
        int clusterCount = 1 + random.nextInt(3);

        for (int c = 0; c < clusterCount; c++) {
            int clusterOffset = (int) (((double) (c + 1) / (clusterCount + 1)) * totalMinutes);
            int commitsInCluster = 1 + random.nextInt(3);

            for (int i = 0; i < commitsInCluster; i++) {
                int offset = clusterOffset + (i * (5 + random.nextInt(15)));
                if (offset < totalMinutes) {
                    times.add(d.shiftStart().plusMinutes(offset));
                }
            }
        }

        times.sort(Comparator.naturalOrder());

        if (day.workedNight()) {
            int nightCommit = 3 + random.nextInt(5);
            for (int i = 0; i < nightCommit; i++) {
                int nightHour = (22 + random.nextInt(4)) % 24;
                LocalDateTime nightTs = date.atTime(nightHour, random.nextInt(60));
                times.add(nightTs);
            }
            times.sort(Comparator.naturalOrder());
        }

        return times;
    }

    private List<LocalDateTime> generateJiraActivityTimes(WorkDayParams day, LocalDate date, ProfileType type, double phase) {
        WorkDayParams d = adjustToDate(day, date);
        List<LocalDateTime> times = new ArrayList<>();

        boolean silent = (type == ProfileType.DP_CYNICAL || type == ProfileType.BURNED_OUT) && phase > 0.4;
        if (silent && random.nextDouble() < phase * 0.7) return times;

        int activityCount = 1 + random.nextInt(4);
        for (int i = 0; i < activityCount; i++) {
            int offset = random.nextInt(d.workMinutes());
            times.add(d.shiftStart().plusMinutes(offset));
        }
        times.sort(Comparator.naturalOrder());
        return times;
    }

    private List<GitCommit> buildCommits(Employee emp, List<LocalDateTime> times, ProfileType type, double phase, WorkDayParams day) {
        List<GitCommit> commits = new ArrayList<>();

        for (LocalDateTime ts : times) {
            GitCommit c = new GitCommit();
            c.setExternalHash(generateHash());
            c.setEmployee(emp);
            c.setCommittedAt(ts);

            String msg = pickCommitMessage(type, phase, day.productive());
            c.setMessage(msg);
            c.setMessageLength(msg.length());
            commits.add(c);
        }

        return commits;
    }

    private String pickCommitMessage(ProfileType type, double phase, boolean productive) {
        if (type == ProfileType.EE_EXHAUSTED && phase > 0.7 && random.nextDouble() < phase * 0.5) {
            return COMMITS_DEGRADED.get(random.nextInt(COMMITS_DEGRADED.size()));
        }
        if (type == ProfileType.EE_EXHAUSTED && phase > 0.5 && random.nextDouble() < 0.4) {
            return COMMITS_RUSHED.get(random.nextInt(COMMITS_RUSHED.size()));
        }

        double degradationChance = switch (type) {
            case DP_CYNICAL -> 0.6 + phase * 0.3;
            case BURNED_OUT -> 0.7 + phase * 0.2;
            default -> 0.05;
        };

        if (!productive || random.nextDouble() < degradationChance) {
            return COMMITS_DEGRADED.get(random.nextInt(COMMITS_DEGRADED.size()));
        }

        double r = random.nextDouble();
        if (r < 0.4) return COMMITS_FEATURE.get(random.nextInt(COMMITS_FEATURE.size()));
        if (r < 0.65) return COMMITS_FIX.get(random.nextInt(COMMITS_FIX.size()));
        if (r < 0.85) return COMMITS_REFACTOR.get(random.nextInt(COMMITS_REFACTOR.size()));
        return COMMITS_DOCS_TESTS.get(random.nextInt(COMMITS_DOCS_TESTS.size()));
    }

    private List<GitPullRequest> buildPullRequests(Employee emp, WorkDayParams base, LocalDate date, ProfileType type, double phase) {
        WorkDayParams day = adjustToDate(base, date);
        List<GitPullRequest> prs = new ArrayList<>();

        double prChance = switch (type) {
            case DP_CYNICAL -> 0.15 - phase * 0.05;
            case RPA_STAGNANT -> 0.2;
            case BURNED_OUT -> 0.1 + (1 - phase) * 0.1;
            default -> 0.25;
        };

        if (random.nextDouble() > prChance) return prs;

        GitPullRequest pr = new GitPullRequest();
        pr.setEmployee(emp);
        pr.setExternalId("PR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        int leadTimeMinutes = computeLeadTime(type, phase);
        LocalDateTime prCreated = day.shiftStart().minusMinutes(leadTimeMinutes);
        pr.setCreatedAt(prCreated);

        boolean mergedToday = leadTimeMinutes <= day.workMinutes() || random.nextDouble() < 0.7;
        if (mergedToday) {
            LocalDateTime mergedAt = day.shiftStart().plusMinutes(
                    Math.min(leadTimeMinutes, (int) (day.workMinutes() * 0.8) + random.nextInt(60))
            );
            pr.setMergedAt(mergedAt);
            pr.setClosedAt(mergedAt.plusMinutes(random.nextInt(10)));
            pr.setMergeableState(computeMergeableState(type, phase));
        } else {
            pr.setMergeableState(type == ProfileType.RPA_STAGNANT ? "dirty" : "blocked");
        }

        pr.setLeadTimeMinutes(leadTimeMinutes);
        prs.add(pr);
        return prs;
    }

    private int computeLeadTime(ProfileType type, double phase) {
        return switch (type) {
            case RPA_STAGNANT -> (int) (1440 + phase * 4320 + random.nextInt(1440)); // 1–4 дня+
            case BURNED_OUT -> (int) (720 + phase * 2880 + random.nextInt(720));
            case EE_EXHAUSTED -> 60 + random.nextInt(180);
            default -> 120 + random.nextInt(360);
        };
    }

    private String computeMergeableState(ProfileType type, double phase) {
        if (type == ProfileType.RPA_STAGNANT) return random.nextDouble() < 0.6 + phase * 0.3 ? "dirty" : "clean";
        if (type == ProfileType.BURNED_OUT && random.nextDouble() < phase * 0.4) return "dirty";
        return random.nextDouble() < 0.1 ? "unknown" : "clean";
    }

    private int computeTaskCount(ProfileType type, double phase, boolean productive) {
        if (!productive) return random.nextDouble() < 0.5 ? 1 : 0;
        return switch (type) {
            case DP_CYNICAL -> phase > 0.6 ? (random.nextBoolean() ? 1 : 0) : 1;
            case EE_EXHAUSTED -> 1 + (phase > 0.5 ? random.nextInt(2) : 0);
            default -> 1;
        };
    }

    private JiraTask buildJiraTask(Employee emp, Project project, int seq, WorkDayParams base, LocalDate date, ProfileType type, double phase) {
        WorkDayParams day = adjustToDate(base, date);

        JiraTask task = new JiraTask();
        task.setExternalId(project.getJiraKey() + "-" + seq);
        task.setEmployee(emp);
        task.setProject(project);

        int daysAgo = 1 + random.nextInt(5);
        task.setCreatedAt(day.shiftStart().minusDays(daysAgo).withHour(9 + random.nextInt(4)));

        boolean completed = switch (type) {
            case RPA_STAGNANT -> phase < 0.5 || random.nextDouble() > phase;
            case BURNED_OUT -> random.nextDouble() > phase * 0.4;
            default -> true;
        };

        task.setStatus(completed ? "Done" : (random.nextBoolean() ? "In Progress" : "Code Review"));
        task.setUpdatedAt(day.shiftEnd());

        return task;
    }

    private List<JiraTaskComment> buildComments(Employee emp, JiraTask task, List<LocalDateTime> times, ProfileType type, double phase) {
        List<JiraTaskComment> comments = new ArrayList<>();
        if (times.isEmpty()) return comments;

        boolean silent = type == ProfileType.DP_CYNICAL && random.nextDouble() < 0.7 + phase * 0.2;

        for (LocalDateTime ts : times) {
            JiraTaskComment comment = new JiraTaskComment();
            comment.setTask(task);
            comment.setEmployee(emp);
            comment.setCreatedAt(ts);

            if (silent && random.nextDouble() < phase * 0.6) {
                String shortMsg = JIRA_COMMENTS_SHORT.get(random.nextInt(JIRA_COMMENTS_SHORT.size()));
                comment.setBodyLength(shortMsg.length());
                comment.setAttachmentsCount(0);
            } else if (random.nextDouble() < 0.25) {
                comment.setBodyLength(0);
                comment.setAttachmentsCount(1 + random.nextInt(3));
            } else {
                boolean detailed = random.nextDouble() > 0.4;
                String text = detailed
                        ? JIRA_COMMENTS_DETAILED.get(random.nextInt(JIRA_COMMENTS_DETAILED.size()))
                        : JIRA_COMMENTS_SHORT.get(random.nextInt(JIRA_COMMENTS_SHORT.size()));
                comment.setBodyLength(text.length());
                comment.setAttachmentsCount(0);
            }

            comments.add(comment);
        }

        return comments;
    }

    private List<JiraTaskChangelog> buildChangelogs(JiraTask task, WorkDayParams base, LocalDate date, ProfileType type, double phase) {
        WorkDayParams day = adjustToDate(base, date);
        List<JiraTaskChangelog> logs = new ArrayList<>();

        LocalDateTime t = task.getCreatedAt().plusHours(1 + random.nextInt(3));

        logs.add(createLog(task, "To Do", "In Progress", t));
        t = t.plusHours(2 + random.nextInt(6));

        boolean slowReview = (type == ProfileType.RPA_STAGNANT && phase > 0.4);
        if (!slowReview || random.nextDouble() > phase) {
            logs.add(createLog(task, "In Progress", "Code Review", t));
            t = t.plusHours(slowReview ? 24 + random.nextInt(48) : 1 + random.nextInt(4));
        }

        if ("Done".equals(task.getStatus())) {
            logs.add(createLog(task, "Code Review", "Done", t.isBefore(day.shiftEnd()) ? t : day.shiftEnd().minusMinutes(10)));
        }

        boolean reopen = switch (type) {
            case RPA_STAGNANT -> random.nextDouble() < 0.4 + phase * 0.4;
            case BURNED_OUT -> random.nextDouble() < phase * 0.3;
            default -> random.nextDouble() < 0.05;
        };

        if (reopen && "Done".equals(task.getStatus())) {
            LocalDateTime reopenTime = t.plusHours(1 + random.nextInt(3));
            logs.add(createLog(task, "Done", "In Progress", reopenTime));
        }

        return logs;
    }

    private JiraTaskChangelog createLog(JiraTask task, String from, String to, LocalDateTime time) {
        JiraTaskChangelog log = new JiraTaskChangelog();
        log.setTask(task);
        log.setFieldName("status");
        log.setFromString(from);
        log.setToString(to);
        log.setCreatedAt(time);
        return log;
    }

    private String generateHash() {
        return UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}