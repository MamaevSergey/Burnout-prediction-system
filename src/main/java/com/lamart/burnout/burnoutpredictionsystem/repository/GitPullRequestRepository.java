package com.lamart.burnout.burnoutpredictionsystem.repository;

import com.lamart.burnout.burnoutpredictionsystem.entity.GitPullRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface GitPullRequestRepository extends JpaRepository<GitPullRequest, Long> {
    boolean existsByExternalId(String externalId);
    List<GitPullRequest> findAllByEmployeeIdAndMergedAtBetween(UUID employeeId, LocalDateTime mergedAtAfter, LocalDateTime mergedAtBefore);
    List<GitPullRequest> findAllByMergedAtBetween(LocalDateTime start, LocalDateTime end);
}
