package com.lamart.burnout.burnoutpredictionsystem.repository;

import com.lamart.burnout.burnoutpredictionsystem.entity.JiraTaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface JiraTaskCommentRepository extends JpaRepository<JiraTaskComment, Long> {
    int countByEmployeeIdAndCreatedAtBetween(UUID employeeId, LocalDateTime start, LocalDateTime end);
    boolean existsByTaskIdAndCreatedAt(Long taskId, LocalDateTime createdAt);
    boolean existsByTaskIdAndEmployeeIdAndCreatedAt(Long taskId, UUID employeeId, LocalDateTime createdAt);
    List<JiraTaskComment> findAllByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
