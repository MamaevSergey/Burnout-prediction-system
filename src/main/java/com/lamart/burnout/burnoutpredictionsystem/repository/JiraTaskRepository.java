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
    Optional<JiraTask> findByExternalId(String externalId);
    List<JiraTask> findAllByUpdatedAtBetween(LocalDateTime start, LocalDateTime end);
}