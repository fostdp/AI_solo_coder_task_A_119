package com.metrology.balance.service;

import com.metrology.balance.entity.Balance;
import com.metrology.balance.entity.BalanceMeasurement;
import com.metrology.balance.repository.BalanceMeasurementRepository;
import com.metrology.balance.repository.BalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final BalanceRepository balanceRepository;
    private final BalanceMeasurementRepository measurementRepository;

    public List<Balance> getAllBalances() {
        return balanceRepository.findAll();
    }

    public Page<Balance> getBalancesPage(int page, int size, String balanceType) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        if (balanceType != null && !balanceType.isEmpty()) {
            return balanceRepository.findByBalanceType(balanceType, pageable);
        }
        return balanceRepository.findAll(pageable);
    }

    public Optional<Balance> getBalanceById(Long id) {
        return balanceRepository.findById(id);
    }

    public Optional<Balance> getBalanceByCode(String code) {
        return balanceRepository.findByBalanceCode(code);
    }

    public List<Balance> getBalancesByDynasty(Integer dynastyId) {
        return balanceRepository.findByDynastyId(dynastyId);
    }

    public List<Balance> searchBalances(String keyword) {
        return balanceRepository.searchByKeyword(keyword);
    }

    public Balance saveBalance(Balance balance) {
        return balanceRepository.save(balance);
    }

    public void deleteBalance(Long id) {
        balanceRepository.deleteById(id);
    }

    public Map<String, Object> getBalanceStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long totalCount = balanceRepository.count();
        long equalArmCount = balanceRepository.countByBalanceType("EQUAL_ARM");
        long unequalArmCount = balanceRepository.countByBalanceType("UNEQUAL_ARM");

        stats.put("totalCount", totalCount);
        stats.put("equalArmCount", equalArmCount);
        stats.put("unequalArmCount", unequalArmCount);

        LocalDateTime recentTime = LocalDateTime.now().minusHours(24);
        long recentMeasurements = 0;
        List<Balance> allBalances = balanceRepository.findAll();
        for (Balance b : allBalances) {
            recentMeasurements += measurementRepository.countByBalanceIdAndTimeAfter(b.getId(), recentTime);
        }
        stats.put("recentMeasurements", recentMeasurements);

        List<BalanceMeasurement> alertMeasurements = measurementRepository.findAlertMeasurements();
        stats.put("activeAlerts", alertMeasurements.size());

        return stats;
    }

    public Map<String, Object> getBalanceDetail(Long id) {
        Map<String, Object> detail = new HashMap<>();

        Optional<Balance> balanceOpt = balanceRepository.findById(id);
        if (!balanceOpt.isPresent()) {
            return detail;
        }

        Balance balance = balanceOpt.get();
        detail.put("balance", balance);

        List<BalanceMeasurement> measurements = measurementRepository
                .findByBalanceIdOrderByMeasurementTimeDesc(id);
        detail.put("measurementCount", measurements.size());

        if (!measurements.isEmpty()) {
            detail.put("latestMeasurement", measurements.get(0));

            Double avgError = measurementRepository.findAverageErrorByBalanceId(id);
            Double stdDev = measurementRepository.findStdDevErrorByBalanceId(id);
            detail.put("averageError", avgError);
            detail.put("stdDeviation", stdDev);
        }

        return detail;
    }
}
