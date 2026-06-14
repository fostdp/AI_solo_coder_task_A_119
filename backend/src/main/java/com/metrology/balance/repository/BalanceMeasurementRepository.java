package com.metrology.balance.repository;

import com.metrology.balance.entity.BalanceMeasurement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BalanceMeasurementRepository extends JpaRepository<BalanceMeasurement, Long> {

    List<BalanceMeasurement> findByBalanceIdOrderByMeasurementTimeDesc(Long balanceId);

    Page<BalanceMeasurement> findByBalanceIdOrderByMeasurementTimeDesc(Long balanceId, Pageable pageable);

    List<BalanceMeasurement> findByBalanceIdAndMeasurementTimeBetweenOrderByMeasurementTime(
            Long balanceId, LocalDateTime startTime, LocalDateTime endTime);

    @Query("SELECT m FROM BalanceMeasurement m WHERE m.balanceId = :balanceId " +
           "AND m.measurementTime >= :startTime ORDER BY m.measurementTime DESC")
    List<BalanceMeasurement> findRecentMeasurements(
            @Param("balanceId") Long balanceId,
            @Param("startTime") LocalDateTime startTime);

    @Query("SELECT m FROM BalanceMeasurement m WHERE m.isAlert = true " +
           "ORDER BY m.measurementTime DESC")
    List<BalanceMeasurement> findAlertMeasurements();

    @Query(value = "SELECT DISTINCT ON (balance_id) * FROM balance_measurements " +
                   "ORDER BY balance_id, measurement_time DESC", nativeQuery = true)
    List<BalanceMeasurement> findLatestForEachBalance();

    @Query("SELECT AVG(m.weighingError) FROM BalanceMeasurement m WHERE m.balanceId = :balanceId")
    Double findAverageErrorByBalanceId(@Param("balanceId") Long balanceId);

    @Query("SELECT STDDEV(m.weighingError) FROM BalanceMeasurement m WHERE m.balanceId = :balanceId")
    Double findStdDevErrorByBalanceId(@Param("balanceId") Long balanceId);

    @Query("SELECT COUNT(m) FROM BalanceMeasurement m WHERE m.balanceId = :balanceId " +
           "AND m.measurementTime >= :startTime")
    long countByBalanceIdAndTimeAfter(@Param("balanceId") Long balanceId,
                                       @Param("startTime") LocalDateTime startTime);
}
