package com.lamart.burnout.burnoutpredictionsystem.repository;

import com.lamart.burnout.burnoutpredictionsystem.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
}