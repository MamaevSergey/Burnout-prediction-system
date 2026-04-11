package com.lamart.burnout.burnoutpredictionsystem.repository;

import com.lamart.burnout.burnoutpredictionsystem.entity.GitCommit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface GitCommitRepository extends JpaRepository<GitCommit, Long> {
    List<GitCommit> findAllByEmployeeIdAndCommittedAtBetween(UUID employeeId, LocalDateTime start, LocalDateTime end);
    boolean existsByExternalHash(String externalHash);
    List<GitCommit> findAllByCommittedAtBetween(LocalDateTime start, LocalDateTime end);
}
