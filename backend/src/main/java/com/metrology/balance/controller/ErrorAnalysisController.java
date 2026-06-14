package com.metrology.balance.controller;

import com.metrology.balance.dto.MonteCarloResult;
import com.metrology.balance.entity.ErrorAnalysis;
import com.metrology.balance.modules.error_simulator.ErrorSimulatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/error-analysis")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ErrorAnalysisController {

    private final ErrorSimulatorService errorAnalysisService;

    @PostMapping("/{balanceId}")
    public ResponseEntity<?> runAnalysis(
            @PathVariable Long balanceId,
            @RequestParam(defaultValue = "100000") int simulationCount) {
        try {
            MonteCarloResult result = errorAnalysisService.runMonteCarloSimulation(balanceId, simulationCount);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("运行误差分析失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{balanceId}/history")
    public ResponseEntity<?> getAnalysisHistory(@PathVariable Long balanceId) {
        try {
            List<ErrorAnalysis> history = errorAnalysisService.getAnalysisHistory(balanceId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("获取分析历史失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{balanceId}/latest")
    public ResponseEntity<?> getLatestAnalysis(@PathVariable Long balanceId) {
        try {
            ErrorAnalysis analysis = errorAnalysisService.getLatestAnalysis(balanceId);
            if (analysis == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            log.error("获取最新分析失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
