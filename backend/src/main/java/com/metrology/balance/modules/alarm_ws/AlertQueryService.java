package com.metrology.balance.modules.alarm_ws;

import com.metrology.balance.entity.Alert;
import com.metrology.balance.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertQueryService {

    private final AlertRepository alertRepository;

    public List<Alert> getAlertsByBalance(Long balanceId) {
        return alertRepository.findByBalanceIdOrderByCreatedAtDesc(balanceId);
    }

    public List<Alert> getActiveAlerts() {
        return alertRepository.findByIsResolvedFalseOrderByCreatedAtDesc();
    }

    public Alert resolveAlert(Long alertId) {
        return alertRepository.findById(alertId).map(alert -> {
            alert.setResolved(true);
            alert.setResolvedAt(java.time.LocalDateTime.now());
            return alertRepository.save(alert);
        }).orElse(null);
    }
}
