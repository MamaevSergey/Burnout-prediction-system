package com.lamart.burnout.burnoutpredictionsystem.repository;

import com.lamart.burnout.burnoutpredictionsystem.entity.JiraTaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface JiraTaskCommentRepository extends JpaRepository<JiraTaskComment, Long> {

    @Query("SELECT COUNT(c) > 0 FROM JiraTaskComment c WHERE c.task.internalId = :taskId AND c.employee.id = :employeeId AND c.createdAt = :createdAt")
    boolean existsByTaskIdAndEmployeeIdAndCreatedAt(@Param("taskId") Long taskId, @Param("employeeId") UUID employeeId, @Param("createdAt") LocalDateTime createdAt);

    List<JiraTaskComment> findAllByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
