package com.metrology.balance.repository;

import com.metrology.balance.entity.ErrorAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ErrorAnalysisRepository extends JpaRepository<ErrorAnalysis, Long> {

    List<ErrorAnalysis> findByBalanceIdOrderByAnalysisTimeDesc(Long balanceId);

    Page<ErrorAnalysis> findByBalanceIdOrderByAnalysisTimeDesc(Long balanceId, Pageable pageable);

    @Query("SELECT e FROM ErrorAnalysis e WHERE e.balanceId = :balanceId " +
           "ORDER BY e.analysisTime DESC")
    Optional<ErrorAnalysis> findLatestByBalanceId(@Param("balanceId") Long balanceId);
}
