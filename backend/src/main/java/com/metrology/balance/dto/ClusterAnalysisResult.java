package com.metrology.balance.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClusterAnalysisResult {

    private Integer clusterCount;
    private BigDecimal silhouetteScore;
    private String method;

    private BigDecimal jinStandard;
    private BigDecimal liangStandard;

    private List<ClusterInfo> clusters;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ClusterInfo {
        private Integer clusterId;
        private BigDecimal center;
        private Integer sampleCount;
        private BigDecimal minValue;
        private BigDecimal maxValue;
        private BigDecimal stdDev;
    }
}
