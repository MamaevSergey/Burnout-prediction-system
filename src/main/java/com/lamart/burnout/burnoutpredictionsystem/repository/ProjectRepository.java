package com.lamart.burnout.burnoutpredictionsystem.repository;

import com.lamart.burnout.burnoutpredictionsystem.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    Project findByJiraKey(String jiraKey);
}