package com.metrology.balance.repository;

import com.metrology.balance.entity.Weight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WeightRepository extends JpaRepository<Weight, Long> {

    Optional<Weight> findByWeightCode(String weightCode);

    List<Weight> findByDynastyId(Integer dynastyId);

    @Query("SELECT w FROM Weight w WHERE w.dynastyId = :dynastyId ORDER BY w.nominalMass")
    List<Weight> findByDynastyIdOrderByNominalMass(@Param("dynastyId") Integer dynastyId);

    @Query("SELECT w FROM Weight w WHERE w.nominalMass BETWEEN :minMass AND :maxMass")
    List<Weight> findByNominalMassBetween(
            @Param("minMass") java.math.BigDecimal minMass,
            @Param("maxMass") java.math.BigDecimal maxMass);

    @Query("SELECT w.dynastyId, AVG(w.actualMass) FROM Weight w GROUP BY w.dynastyId")
    List<Object[]> findAverageActualMassByDynasty();

    @Query("SELECT w.actualMass FROM Weight w WHERE w.dynastyId = :dynastyId")
    List<java.math.BigDecimal> findActualMassesByDynastyId(@Param("dynastyId") Integer dynastyId);
}
