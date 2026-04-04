package com.lamart.burnout.burnoutpredictionsystem.repository;

import com.lamart.burnout.burnoutpredictionsystem.entity.JiraTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JiraTaskRepository extends JpaRepository<JiraTask, Long> {
    List<JiraTask> findAllByEmployeeIdAndUpdatedAtBetween(UUID employeeId, LocalDateTime start, LocalDateTime end);
    Optional<JiraTask> findByExternalId(String externalId);
}