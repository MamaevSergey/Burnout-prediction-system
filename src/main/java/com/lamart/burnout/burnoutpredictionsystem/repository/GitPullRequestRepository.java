package com.lamart.burnout.burnoutpredictionsystem.repository;

import com.lamart.burnout.burnoutpredictionsystem.entity.GitPullRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GitPullRequestRepository extends JpaRepository<GitPullRequest, Long> {
}
