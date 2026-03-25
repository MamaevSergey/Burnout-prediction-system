package com.lamart.burnout.burnoutpredictionsystem.repository;

import com.lamart.burnout.burnoutpredictionsystem.entity.BurnoutScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BurnoutScoreRepository extends JpaRepository<BurnoutScore, Long> {
    @Query("SELECT b FROM BurnoutScore b WHERE b.calculatedAt = (SELECT MAX(b2.calculatedAt) FROM BurnoutScore b2 WHERE b2.employee = b.employee)")
    List<BurnoutScore> findLatestScores();
}