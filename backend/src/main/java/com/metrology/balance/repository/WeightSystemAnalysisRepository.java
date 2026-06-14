package com.metrology.balance.repository;

import com.metrology.balance.entity.WeightSystemAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WeightSystemAnalysisRepository extends JpaRepository<WeightSystemAnalysis, Long> {

    List<WeightSystemAnalysis> findByDynastyIdOrderByAnalysisTimeDesc(Integer dynastyId);

    @Query("SELECT w FROM WeightSystemAnalysis w WHERE w.dynastyId = :dynastyId " +
           "ORDER BY w.analysisTime DESC")
    Optional<WeightSystemAnalysis> findLatestByDynastyId(@Param("dynastyId") Integer dynastyId);

    List<WeightSystemAnalysis> findAllByOrderByAnalysisTimeDesc();
}
