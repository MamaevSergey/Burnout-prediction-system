package com.lamart.burnout.burnoutpredictionsystem.repository;

import com.lamart.burnout.burnoutpredictionsystem.entity.BurnoutScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BurnoutScoreRepository extends JpaRepository<BurnoutScore, Long> {
    @Query("SELECT b FROM BurnoutScore b WHERE b.calculatedAt = (SELECT MAX(b2.calculatedAt) FROM BurnoutScore b2 WHERE b2.employee = b.employee)")
    List<BurnoutScore> findLatestScores();
    Optional<BurnoutScore> findTopByEmployeeIdOrderByTargetDateDesc(UUID employeeId);
    @Query("SELECT MAX(b.targetDate) FROM BurnoutScore b")
    LocalDate findMaxTargetDate();

    List<BurnoutScore> findByTargetDate(LocalDate targetDate);
    Optional<BurnoutScore> findTopByEmployeeIdAndTargetDate(UUID employeeId, LocalDate targetDate);

    Optional<BurnoutScore> findTopByEmployeeIdAndTargetDateBeforeOrderByTargetDateDesc(UUID employeeId, LocalDate targetDate);
    boolean existsByTargetDate(LocalDate targetDate);

    @Query("SELECT b FROM BurnoutScore b WHERE b.employee.id IN :employeeIds " +
            "AND b.targetDate = (SELECT MAX(b2.targetDate) FROM BurnoutScore b2 WHERE b2.employee.id = b.employee.id)")
    List<BurnoutScore> findLatestScoresByEmployeeIds(@Param("employeeIds") List<UUID> employeeIds);
}