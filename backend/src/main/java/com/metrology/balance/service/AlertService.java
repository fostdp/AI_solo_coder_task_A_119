package com.metrology.balance.service;

import com.metrology.balance.entity.Alert;
import com.metrology.balance.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;

    public List<Alert> getActiveAlerts() {
        return alertRepository.findByIsResolvedFalseOrderByCreatedAtDesc();
    }

    public Page<Alert> getActiveAlertsPage(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return alertRepository.findByIsResolvedFalseOrderByCreatedAtDesc(pageable);
    }

    public List<Alert> getAlertsByBalance(Long balanceId) {
        return alertRepository.findByBalanceIdOrderByCreatedAtDesc(balanceId);
    }

    public List<Alert> getAlertsByLevel(String level) {
        return alertRepository.findByAlertLevelAndIsResolvedFalseOrderByCreatedAtDesc(level);
    }

    public Optional<Alert> getAlertById(Long id) {
        return alertRepository.findById(id);
    }

    @Transactional
    public Alert resolveAlert(Long id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("告警不存在: " + id));

        alert.setIsResolved(true);
        alert.setResolvedAt(LocalDateTime.now());

        log.info("告警已解决: {}", id);
        return alertRepository.save(alert);
    }

    @Transactional
    public int resolveAllAlerts() {
        List<Alert> activeAlerts = alertRepository.findByIsResolvedFalseOrderByCreatedAtDesc();
        LocalDateTime now = LocalDateTime.now();

        for (Alert alert : activeAlerts) {
            alert.setIsResolved(true);
            alert.setResolvedAt(now);
        }

        alertRepository.saveAll(activeAlerts);
        return activeAlerts.size();
    }

    public long getUnresolvedCount() {
        return alertRepository.countUnresolved();
    }

    public long getUnresolvedCountByLevel(String level) {
        return alertRepository.countUnresolvedByLevel(level);
    }

    public Map<String, Object> getAlertStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long totalUnresolved = alertRepository.countUnresolved();
        long criticalCount = alertRepository.countUnresolvedByLevel("CRITICAL");
        long warningCount = alertRepository.countUnresolvedByLevel("WARNING");
        long infoCount = alertRepository.countUnresolvedByLevel("INFO");

        stats.put("totalUnresolved", totalUnresolved);
        stats.put("criticalCount", criticalCount);
        stats.put("warningCount", warningCount);
        stats.put("infoCount", infoCount);

        List<Object[]> byBalance = alertRepository.countUnresolvedByBalance();
        stats.put("byBalance", byBalance);

        return stats;
    }

    public List<Alert> getRecentAlerts(LocalDateTime since) {
        return alertRepository.findAlertsAfterTime(since);
    }
}
