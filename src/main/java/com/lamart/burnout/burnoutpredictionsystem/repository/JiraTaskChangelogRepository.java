package com.lamart.burnout.burnoutpredictionsystem.repository;

import com.lamart.burnout.burnoutpredictionsystem.entity.JiraTaskChangelog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface JiraTaskChangelogRepository extends JpaRepository<JiraTaskChangelog, Long> {
    @Query("SELECT c FROM JiraTaskChangelog c JOIN FETCH c.task t JOIN FETCH t.employee e " +
            "WHERE c.createdAt BETWEEN :start AND :end " +
            "AND c.fieldName = 'status' " +
            "AND (LOWER(c.fromString) IN ('done', 'closed', 'готово') OR LOWER(c.toString) IN ('in progress', 'в работе', 'reopened'))")
    List<JiraTaskChangelog> findReopensBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(c) > 0 FROM JiraTaskChangelog c WHERE c.task.internalId = :taskId AND c.fieldName = :fieldName AND c.createdAt = :createdAt")
    boolean existsByTaskIdAndFieldNameAndCreatedAt(@Param("taskId") Long taskId, @Param("fieldName") String fieldName, @Param("createdAt") LocalDateTime createdAt);
}
