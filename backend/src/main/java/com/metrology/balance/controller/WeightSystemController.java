package com.metrology.balance.controller;

import com.metrology.balance.dto.ClusterAnalysisResult;
import com.metrology.balance.entity.Weight;
import com.metrology.balance.entity.WeightSystemAnalysis;
import com.metrology.balance.repository.WeightRepository;
import com.metrology.balance.modules.metrology_analyzer.MetrologyAnalyzerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/weight-system")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WeightSystemController {

    private final MetrologyAnalyzerService analysisService;
    private final WeightRepository weightRepository;

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeWeightSystem(
            @RequestParam(required = false) Integer dynastyId,
            @RequestParam(required = false, defaultValue = "0") int clusterCount) {
        try {
            ClusterAnalysisResult result = analysisService.analyzeWeightSystem(dynastyId, clusterCount);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("权衡制度分析失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> getAnalysisHistory(
            @RequestParam(required = false) Integer dynastyId) {
        try {
            List<WeightSystemAnalysis> history = analysisService.getAnalysisHistory(dynastyId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("获取分析历史失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/latest")
    public ResponseEntity<?> getLatestAnalysis(
            @RequestParam(required = false) Integer dynastyId) {
        try {
            WeightSystemAnalysis analysis = analysisService.getLatestAnalysis(dynastyId);
            if (analysis == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            log.error("获取最新分析失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/weights")
    public ResponseEntity<?> getWeights(
            @RequestParam(required = false) Integer dynastyId) {
        try {
            List<Weight> weights;
            if (dynastyId != null) {
                weights = weightRepository.findByDynastyIdOrderByNominalMass(dynastyId);
            } else {
                weights = weightRepository.findAll();
            }
            return ResponseEntity.ok(weights);
        } catch (Exception e) {
            log.error("获取砝码列表失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/weights/{id}")
    public ResponseEntity<?> getWeightById(@PathVariable Long id) {
        try {
            return weightRepository.findById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("获取砝码详情失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
