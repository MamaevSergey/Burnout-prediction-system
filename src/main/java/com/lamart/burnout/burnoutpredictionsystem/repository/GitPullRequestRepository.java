package com.lamart.burnout.burnoutpredictionsystem.repository;

import com.lamart.burnout.burnoutpredictionsystem.entity.GitPullRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface GitPullRequestRepository extends JpaRepository<GitPullRequest, Long> {
    List<GitPullRequest> findAllByExternalIdIn(List<String> externalIds);

    @Query("SELECT pr FROM GitPullRequest pr WHERE pr.employee.id IN :empIds AND ((pr.createdAt BETWEEN :start AND :end) OR (pr.mergedAt BETWEEN :start AND :end))")
    List<GitPullRequest> findActivePRsForEmployees(@Param("empIds") List<UUID> empIds, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
