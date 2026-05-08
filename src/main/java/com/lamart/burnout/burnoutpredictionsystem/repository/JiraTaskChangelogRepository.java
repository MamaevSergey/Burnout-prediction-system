package com.lamart.burnout.burnoutpredictionsystem.repository;

import com.lamart.burnout.burnoutpredictionsystem.entity.JiraTaskChangelog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface JiraTaskChangelogRepository extends JpaRepository<JiraTaskChangelog, Long> {
    @Query("SELECT COUNT(c) > 0 FROM JiraTaskChangelog c WHERE c.task.internalId = :taskId AND c.fieldName = :fieldName AND c.createdAt = :createdAt")
    boolean existsByTaskIdAndFieldNameAndCreatedAt(@Param("taskId") Long taskId, @Param("fieldName") String fieldName, @Param("createdAt") LocalDateTime createdAt);
    List<JiraTaskChangelog> findByTaskEmployeeIdInAndCreatedAtBetween(List<UUID> employeeIds, LocalDateTime start, LocalDateTime end);
}
