package com.metrology.balance.controller;

import com.metrology.balance.entity.Alert;
import com.metrology.balance.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<?> getActiveAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Page<Alert> alerts = alertService.getActiveAlertsPage(page, size);
            return ResponseEntity.ok(alerts);
        } catch (Exception e) {
            log.error("获取告警列表失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllActiveAlerts() {
        try {
            List<Alert> alerts = alertService.getActiveAlerts();
            return ResponseEntity.ok(alerts);
        } catch (Exception e) {
            log.error("获取所有活动告警失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getAlertById(@PathVariable Long id) {
        try {
            return alertService.getAlertById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("获取告警详情失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/balance/{balanceId}")
    public ResponseEntity<?> getAlertsByBalance(@PathVariable Long balanceId) {
        try {
            List<Alert> alerts = alertService.getAlertsByBalance(balanceId);
            return ResponseEntity.ok(alerts);
        } catch (Exception e) {
            log.error("按天平获取告警失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/level/{level}")
    public ResponseEntity<?> getAlertsByLevel(@PathVariable String level) {
        try {
            List<Alert> alerts = alertService.getAlertsByLevel(level.toUpperCase());
            return ResponseEntity.ok(alerts);
        } catch (Exception e) {
            log.error("按级别获取告警失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/statistics")
    public ResponseEntity<?> getAlertStatistics() {
        try {
            Map<String, Object> stats = alertService.getAlertStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取告警统计失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<?> resolveAlert(@PathVariable Long id) {
        try {
            Alert alert = alertService.resolveAlert(id);
            return ResponseEntity.ok(alert);
        } catch (Exception e) {
            log.error("处理告警失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/resolve-all")
    public ResponseEntity<?> resolveAllAlerts() {
        try {
            int count = alertService.resolveAllAlerts();
            return ResponseEntity.ok(Map.of("resolvedCount", count));
        } catch (Exception e) {
            log.error("批量处理告警失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
