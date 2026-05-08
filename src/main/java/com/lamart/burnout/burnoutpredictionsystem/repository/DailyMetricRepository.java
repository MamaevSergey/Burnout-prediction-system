package com.lamart.burnout.burnoutpredictionsystem.repository;

import com.lamart.burnout.burnoutpredictionsystem.entity.DailyMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyMetricRepository extends JpaRepository<DailyMetric, Long> {
    List<DailyMetric> findAllByEmployeeIdAndDateBetween(UUID employeeId, LocalDate startDate, LocalDate endDate);
    List<DailyMetric> findByDate(LocalDate date);
    List<DailyMetric> findAllByDateBetween(LocalDate startDate, LocalDate endDate);
}