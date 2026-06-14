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
public class MonteCarloResult {

    private Integer simulationCount;
    private BigDecimal meanError;
    private BigDecimal stdDeviation;
    private BigDecimal combinedUncertainty;
    private BigDecimal expandedUncertainty;
    private BigDecimal coverageFactor;

    private BigDecimal frictionContribution;
    private BigDecimal armLengthContribution;
    private BigDecimal weightContribution;

    private String accuracyGrade;

    private List<BigDecimal> errorSamples;
    private List<BigDecimal> histogramBins;
    private List<Integer> histogramCounts;
}
