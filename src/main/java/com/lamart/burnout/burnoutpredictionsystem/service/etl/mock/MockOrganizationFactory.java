package com.lamart.burnout.burnoutpredictionsystem.service.etl.mock;

import com.lamart.burnout.burnoutpredictionsystem.entity.Employee;
import com.lamart.burnout.burnoutpredictionsystem.entity.Project;
import com.lamart.burnout.burnoutpredictionsystem.entity.Team;
import com.lamart.burnout.burnoutpredictionsystem.util.Anonymizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Component
@RequiredArgsConstructor
public class MockOrganizationFactory {
    private final Random random = new Random(42L);
    private final Anonymizer anonymizer;

    public List<Team> createTeams() {
        return List.of(
                createTeam("Backend Core"),
                createTeam("Frontend Web"),
                createTeam("Mobile App"),
                createTeam("QA & Automation"),
                createTeam("DevOps & Infra")
        );
    }

    private Team createTeam(String name) {
        Team team = new Team();
        team.setName(name);
        return team;
    }

    public List<Project> createProjects() {
        return List.of(
                createProject("Web Portal", "WEB"),
                createProject("Mobile Client", "MOB"),
                createProject("Billing API", "BIL"),
                createProject("Analytics Platform", "ANP")
        );
    }

    private Project createProject(String name, String key) {
        Project project = new Project();
        project.setName(name);
        project.setJiraKey(key);
        return project;
    }

    public List<EmployeeProfile> createEmployees(List<Team> teams) {
        List<ProfileType> profilePool = buildProfilePool();
        Collections.shuffle(profilePool, random);

        List<EmployeeProfile> profiles = new ArrayList<>();

        for (int i = 0; i < profilePool.size(); i++) {
            ProfileType type = profilePool.get(i);
            Employee e = buildEmployee(i + 1, teams);

            double baseProductivity = 0.6 + random.nextDouble() * 0.9;
            boolean earlyBird = random.nextDouble() < 0.55;

            profiles.add(new EmployeeProfile(e, type, baseProductivity, earlyBird));
        }
        return profiles;
    }

    private List<ProfileType> buildProfilePool() {
        List<ProfileType> pool = new ArrayList<>();
        pool.addAll(Collections.nCopies(31, ProfileType.NORMAL));
        pool.addAll(Collections.nCopies(12, ProfileType.ELEVATED_RISK));
        pool.addAll(Collections.nCopies(7,  ProfileType.EE_EXHAUSTED));
        pool.addAll(Collections.nCopies(7,  ProfileType.DP_CYNICAL));
        pool.addAll(Collections.nCopies(5,  ProfileType.RPA_STAGNANT));
        pool.addAll(Collections.nCopies(3,  ProfileType.BURNED_OUT));
        while (pool.size() < 65) pool.add(ProfileType.NORMAL);
        return pool.subList(0, 65);
    }

    private Employee buildEmployee(int index, List<Team> teams) {
        Employee e = new Employee();
        e.setId(anonymizer.hashToUuid("developer_" + index + "@lamart.ru"));
        e.setGithubUsername("dev" + index + "lamart");
        e.setTeam(teams.get(random.nextInt(teams.size())));
        e.setRole(pickRole());
        e.setActive(true);
        return e;
    }

    private String pickRole() {
        double r = random.nextDouble();
        if (r < 0.55) return "Developer";
        if (r < 0.70) return "Senior Developer";
        if (r < 0.82) return "QA Engineer";
        if (r < 0.92) return "Tech Lead";
        return "DevOps Engineer";
    }
}