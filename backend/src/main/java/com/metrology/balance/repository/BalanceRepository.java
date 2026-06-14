package com.metrology.balance.repository;

import com.metrology.balance.entity.Balance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BalanceRepository extends JpaRepository<Balance, Long> {

    Optional<Balance> findByBalanceCode(String balanceCode);

    List<Balance> findByDynastyId(Integer dynastyId);

    List<Balance> findByBalanceType(String balanceType);

    Page<Balance> findByBalanceType(String balanceType, Pageable pageable);

    @Query("SELECT b FROM Balance b WHERE b.dynastyId = :dynastyId AND b.balanceType = :type")
    List<Balance> findByDynastyIdAndBalanceType(
            @Param("dynastyId") Integer dynastyId,
            @Param("type") String balanceType);

    @Query("SELECT b FROM Balance b WHERE b.name LIKE %:keyword% OR b.balanceCode LIKE %:keyword%")
    List<Balance> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT COUNT(b) FROM Balance b WHERE b.balanceType = :type")
    long countByBalanceType(@Param("type") String balanceType);
}
