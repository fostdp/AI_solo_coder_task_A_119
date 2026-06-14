package com.metrology.balance.controller;

import com.metrology.balance.dto.BalanceSensorData;
import com.metrology.balance.entity.Balance;
import com.metrology.balance.entity.BalanceMeasurement;
import com.metrology.balance.modules.mqtt_receiver.MqttReceiverService;
import com.metrology.balance.service.BalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/balances")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BalanceController {

    private final BalanceService balanceService;
    private final MqttReceiverService receiverService;

    @GetMapping
    public ResponseEntity<?> getBalances(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String balanceType) {
        try {
            Page<Balance> balances = balanceService.getBalancesPage(page, size, balanceType);
            Map<String, Object> result = new HashMap<>();
            result.put("content", balances.getContent());
            result.put("totalElements", balances.getTotalElements());
            result.put("totalPages", balances.getTotalPages());
            result.put("currentPage", page);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取天平列表失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllBalances() {
        try {
            List<Balance> balances = balanceService.getAllBalances();
            return ResponseEntity.ok(balances);
        } catch (Exception e) {
            log.error("获取所有天平失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getBalanceById(@PathVariable Long id) {
        try {
            Map<String, Object> detail = balanceService.getBalanceDetail(id);
            if (detail.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(detail);
        } catch (Exception e) {
            log.error("获取天平详情失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<?> getBalanceByCode(@PathVariable String code) {
        try {
            return balanceService.getBalanceByCode(code)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("根据编码获取天平失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/dynasty/{dynastyId}")
    public ResponseEntity<?> getBalancesByDynasty(@PathVariable Integer dynastyId) {
        try {
            List<Balance> balances = balanceService.getBalancesByDynasty(dynastyId);
            return ResponseEntity.ok(balances);
        } catch (Exception e) {
            log.error("按朝代获取天平失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchBalances(@RequestParam String keyword) {
        try {
            List<Balance> balances = balanceService.searchBalances(keyword);
            return ResponseEntity.ok(balances);
        } catch (Exception e) {
            log.error("搜索天平失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createBalance(@RequestBody Balance balance) {
        try {
            Balance saved = balanceService.saveBalance(balance);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("创建天平失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBalance(@PathVariable Long id, @RequestBody Balance balance) {
        try {
            balance.setId(id);
            Balance saved = balanceService.saveBalance(balance);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("更新天平失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBalance(@PathVariable Long id) {
        try {
            balanceService.deleteBalance(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("删除天平失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics() {
        try {
            Map<String, Object> stats = balanceService.getBalanceStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取统计数据失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/measurements")
    public ResponseEntity<?> getMeasurements(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            List<BalanceMeasurement> measurements = receiverService.getMeasurements(id, startTime, endTime);
            return ResponseEntity.ok(measurements);
        } catch (Exception e) {
            log.error("获取测量数据失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/measurements/latest")
    public ResponseEntity<?> getLatestMeasurements(@RequestParam(defaultValue = "20") int limit) {
        try {
            List<BalanceMeasurement> measurements = receiverService.getLatestMeasurements(limit);
            return ResponseEntity.ok(measurements);
        } catch (Exception e) {
            log.error("获取最新测量数据失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/measurements")
    public ResponseEntity<?> addMeasurement(@RequestBody BalanceSensorData sensorData) {
        try {
            BalanceMeasurement measurement = receiverService.processSensorData(sensorData);
            return ResponseEntity.ok(measurement);
        } catch (Exception e) {
            log.error("添加测量数据失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
