package com.metrology.balance.repository;

import com.metrology.balance.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByIsResolvedFalseOrderByCreatedAtDesc();

    Page<Alert> findByIsResolvedFalseOrderByCreatedAtDesc(Pageable pageable);

    List<Alert> findByBalanceIdOrderByCreatedAtDesc(Long balanceId);

    List<Alert> findByAlertLevelAndIsResolvedFalseOrderByCreatedAtDesc(String alertLevel);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.isResolved = false")
    long countUnresolved();

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.isResolved = false AND a.alertLevel = :level")
    long countUnresolvedByLevel(@Param("level") String alertLevel);

    @Query("SELECT a FROM Alert a WHERE a.createdAt >= :startTime ORDER BY a.createdAt DESC")
    List<Alert> findAlertsAfterTime(@Param("startTime") LocalDateTime startTime);

    @Query("SELECT a.balanceId, COUNT(a) FROM Alert a WHERE a.isResolved = false " +
           "GROUP BY a.balanceId ORDER BY COUNT(a) DESC")
    List<Object[]> countUnresolvedByBalance();
}
