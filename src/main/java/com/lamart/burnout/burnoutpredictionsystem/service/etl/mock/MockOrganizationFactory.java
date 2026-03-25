package com.lamart.burnout.burnoutpredictionsystem.service.etl.mock;

import com.lamart.burnout.burnoutpredictionsystem.entity.Employee;
import com.lamart.burnout.burnoutpredictionsystem.entity.Project;
import com.lamart.burnout.burnoutpredictionsystem.entity.Team;
import com.lamart.burnout.burnoutpredictionsystem.util.Anonymizer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class MockOrganizationFactory {
    private final Random random = new Random();

    public List<Team> createTeams() {
        return List.of(
                createTeam("Backend Core"), createTeam("Frontend Web"),
                createTeam("Mobile App"), createTeam("QA Automation")
        );
    }

    public Team createTeam(String name) {
        Team team = new Team();
        team.setName(name);
        return team;
    }

    public List<Project> createProjects() {
        return List.of(
                createProject("Web Portal", "WEB"),
                createProject("Mobile Client", "MOB"),
                createProject("Billing API", "BIL")
        );
    }

    private Project createProject(String name, String key) {
        Project project = new Project();
        project.setName(name);
        project.setJiraKey(key);
        return project;
    }

    public List<EmployeeProfile> createEmployees(List<Team> teams) {
        List<EmployeeProfile> profiles = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            Employee e = new Employee();

            if (i == 1) {
                e.setId(Anonymizer.hashToUuid("1hirokoae@gmail.com"));
                e.setGithubUsername("MamaevSergey");
            } else {
                e.setId(Anonymizer.hashToUuid("developer_" + i + "@lamart.ru"));
                e.setGithubUsername("dev" + i + "_lamart");
            }

            e.setTeam(teams.get(random.nextInt(teams.size())));
            e.setRole("Developer");
            e.setActive(true);

            ProfileType type = ProfileType.NORMAL;
            if (i <= 6) type = ProfileType.EE_EXHAUSTED;
            else if (i <= 12) type = ProfileType.DP_CYNICAL;
            else if (i <= 18) type = ProfileType.RPA_STAGNANT;

            profiles.add(new EmployeeProfile(e, type));
        }
        return profiles;
    }
}