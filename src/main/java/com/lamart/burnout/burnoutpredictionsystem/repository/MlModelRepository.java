package com.lamart.burnout.burnoutpredictionsystem.repository;

import com.lamart.burnout.burnoutpredictionsystem.entity.MlModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MlModelRepository extends JpaRepository<MlModel, Long> {
    MlModel findByIsActiveTrue();
}