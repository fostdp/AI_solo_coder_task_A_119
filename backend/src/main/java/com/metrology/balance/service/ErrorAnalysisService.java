package com.metrology.balance.service;

import com.metrology.balance.dto.MonteCarloResult;
import com.metrology.balance.entity.Balance;
import com.metrology.balance.entity.BalanceMeasurement;
import com.metrology.balance.entity.ErrorAnalysis;
import com.metrology.balance.model.KnifeEdgeWearModel;
import com.metrology.balance.repository.BalanceMeasurementRepository;
import com.metrology.balance.repository.BalanceRepository;
import com.metrology.balance.repository.ErrorAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorAnalysisService {

    private final BalanceRepository balanceRepository;
    private final BalanceMeasurementRepository measurementRepository;
    private final ErrorAnalysisRepository errorAnalysisRepository;

    private static final int DEFAULT_SIMULATION_COUNT = 100000;
    private static final double COVERAGE_FACTOR = 2.0;
    private static final int HISTOGRAM_BINS = 50;

    @Transactional
    public MonteCarloResult runMonteCarloSimulation(Long balanceId, int simulationCount) {
        Balance balance = balanceRepository.findById(balanceId)
                .orElseThrow(() -> new IllegalArgumentException("天平不存在: " + balanceId));

        List<BalanceMeasurement> measurements = measurementRepository
                .findByBalanceIdOrderByMeasurementTimeDesc(balanceId);

        if (measurements.isEmpty()) {
            throw new IllegalStateException("该天平暂无测量数据");
        }

        int count = simulationCount > 0 ? simulationCount : DEFAULT_SIMULATION_COUNT;

        double[] errors = simulateErrors(balance, measurements, count);

        DescriptiveStatistics stats = new DescriptiveStatistics(errors);

        double meanError = stats.getMean();
        double stdDev = stats.getStandardDeviation();

        double frictionError = calculateFrictionError(balance, measurements);
        double armLengthError = calculateArmLengthError(balance, measurements);
        double weightError = calculateWeightError(measurements);

        double totalUncertainty = Math.sqrt(
                frictionError * frictionError +
                armLengthError * armLengthError +
                weightError * weightError
        );

        double frictionContrib = (frictionError * frictionError) / (totalUncertainty * totalUncertainty) * 100;
        double armLengthContrib = (armLengthError * armLengthError) / (totalUncertainty * totalUncertainty) * 100;
        double weightContrib = (weightError * weightError) / (totalUncertainty * totalUncertainty) * 100;

        String accuracyGrade = determineAccuracyGrade(stdDev, measurements);

        int[] histogramCounts = calculateHistogram(errors, HISTOGRAM_BINS);
        List<BigDecimal> histogramBins = calculateHistogramBins(stats.getMin(), stats.getMax(), HISTOGRAM_BINS);
        List<Integer> histogramCountList = new ArrayList<>();
        for (int hc : histogramCounts) {
            histogramCountList.add(hc);
        }

        List<BigDecimal> errorSamples = new ArrayList<>();
        int sampleStep = count / 1000;
        for (int i = 0; i < count; i += sampleStep) {
            errorSamples.add(BigDecimal.valueOf(errors[i]).setScale(8, RoundingMode.HALF_UP));
        }

        MonteCarloResult result = MonteCarloResult.builder()
                .simulationCount(count)
                .meanError(BigDecimal.valueOf(meanError).setScale(8, RoundingMode.HALF_UP))
                .stdDeviation(BigDecimal.valueOf(stdDev).setScale(8, RoundingMode.HALF_UP))
                .combinedUncertainty(BigDecimal.valueOf(stdDev).setScale(8, RoundingMode.HALF_UP))
                .expandedUncertainty(BigDecimal.valueOf(stdDev * COVERAGE_FACTOR).setScale(8, RoundingMode.HALF_UP))
                .coverageFactor(BigDecimal.valueOf(COVERAGE_FACTOR))
                .frictionContribution(BigDecimal.valueOf(frictionContrib).setScale(2, RoundingMode.HALF_UP))
                .armLengthContribution(BigDecimal.valueOf(armLengthContrib).setScale(2, RoundingMode.HALF_UP))
                .weightContribution(BigDecimal.valueOf(weightContrib).setScale(2, RoundingMode.HALF_UP))
                .accuracyGrade(accuracyGrade)
                .errorSamples(errorSamples)
                .histogramBins(histogramBins)
                .histogramCounts(histogramCountList)
                .build();

        saveAnalysisResult(balanceId, result);

        return result;
    }

    private double[] simulateErrors(Balance balance, List<BalanceMeasurement> measurements, int count) {
        double[] errors = new double[count];

        KnifeEdgeWearModel wearModel = KnifeEdgeWearModel.createWithMaterial(balance.getMaterial());

        double totalWearDepth = 0;
        long totalCount = 0;
        double avgTemperature = 20.0;
        double avgHumidity = 50.0;
        double avgTemperatureVar = 5.0;
        double avgHumidityVar = 15.0;

        DescriptiveStatistics armLengthDiffStats = new DescriptiveStatistics();
        DescriptiveStatistics weightErrorStats = new DescriptiveStatistics();

        for (BalanceMeasurement m : measurements) {
            if (m.getKnifeEdgeWearDepth() != null) {
                totalWearDepth = Math.max(totalWearDepth, m.getKnifeEdgeWearDepth().doubleValue());
            }
            totalCount = Math.max(totalCount, measurements.size());
            if (m.getTemperature() != null) {
                avgTemperature = m.getTemperature().doubleValue();
            }
            if (m.getHumidity() != null) {
                avgHumidity = m.getHumidity().doubleValue();
            }
            if (m.getLeftArmLength() != null && m.getRightArmLength() != null) {
                double diff = m.getLeftArmLength().doubleValue() - m.getRightArmLength().doubleValue();
                armLengthDiffStats.addValue(diff);
            }
            if (m.getWeighingError() != null) {
                weightErrorStats.addValue(m.getWeighingError().doubleValue());
            }
        }

        wearModel.setAccumulatedWearDepth(totalWearDepth);
        wearModel.setTotalUsageCount(totalCount);
        if (!measurements.isEmpty()) {
            wearModel.setFirstUsageTime(measurements.get(measurements.size() - 1).getMeasurementTime());
        }

        double armDiffMean = armLengthDiffStats.getN() > 0 ? armLengthDiffStats.getMean() : 0.0;
        double armDiffStd = armLengthDiffStats.getN() > 1 ? armLengthDiffStats.getStandardDeviation() : 0.5;

        double weightErrorMean = weightErrorStats.getN() > 0 ? weightErrorStats.getMean() : 0.0;
        double weightErrorStd = weightErrorStats.getN() > 1 ? weightErrorStats.getStandardDeviation() : 0.01;

        NormalDistribution armDiffDist = new NormalDistribution(armDiffMean, armDiffStd);
        NormalDistribution weightErrorDist = new NormalDistribution(weightErrorMean, weightErrorStd);
        NormalDistribution tempDist = new NormalDistribution(avgTemperature, avgTemperatureVar);
        NormalDistribution humidityDist = new NormalDistribution(avgHumidity, avgHumidityVar);

        double avgNominalMass = 10.0;
        double avgArmLength = 150.0;
        if (!measurements.isEmpty() && measurements.get(0).getNominalMass() != null) {
            avgNominalMass = measurements.get(0).getNominalMass().doubleValue();
        }
        if (balance.getLeftArmLength() != null) {
            avgArmLength = balance.getLeftArmLength().doubleValue();
        }

        KnifeEdgeWearModel.WearReport wearReport = wearModel.getWearReport();
        log.info("天平[{}]磨损状态: {}, 累计磨损深度={}mm, 使用次数={}",
                balance.getBalanceCode(), wearReport.getWearStage(),
                wearReport.getAccumulatedWearDepth(), wearReport.getTotalUsageCount());

        for (int i = 0; i < count; i++) {
            double progressRatio = (double) i / count;
            double simulatedWear = totalWearDepth * (1.0 + progressRatio * 0.5);
            wearModel.setAccumulatedWearDepth(simulatedWear);

            double temperature = Math.max(-10, Math.min(60, tempDist.sample()));
            double humidity = Math.max(0, Math.min(100, humidityDist.sample()));

            double dynamicFriction = wearModel.calculateDynamicFriction(
                    avgNominalMass, temperature, humidity, avgArmLength);

            double armDiff = armDiffDist.sample();
            double weightErr = weightErrorDist.sample();

            double armLengthRatio = armDiff / avgArmLength;
            double armLengthError = avgNominalMass * armLengthRatio;
            double frictionError = dynamicFriction * avgNominalMass;

            double humidityBias = (humidity - 50.0) * 0.00001 * avgNominalMass;
            double tempBias = (temperature - 20.0) * 0.000005 * avgNominalMass;

            double totalError = weightErr + armLengthError + frictionError + humidityBias + tempBias;
            errors[i] = totalError;
        }

        return errors;
    }

    private double calculateFrictionError(Balance balance, List<BalanceMeasurement> measurements) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (BalanceMeasurement m : measurements) {
            if (m.getKnifeEdgeFriction() != null) {
                stats.addValue(m.getKnifeEdgeFriction().doubleValue());
            }
        }
        if (stats.getN() == 0) return 0.001;
        return stats.getStandardDeviation();
    }

    private double calculateArmLengthError(Balance balance, List<BalanceMeasurement> measurements) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        double avgMass = 10.0;

        for (BalanceMeasurement m : measurements) {
            if (m.getLeftArmLength() != null && m.getRightArmLength() != null && m.getNominalMass() != null) {
                double ratio = (m.getLeftArmLength().doubleValue() - m.getRightArmLength().doubleValue())
                        / m.getLeftArmLength().doubleValue();
                stats.addValue(ratio * m.getNominalMass().doubleValue());
            }
        }

        if (stats.getN() == 0) return avgMass * 0.001;
        return stats.getStandardDeviation();
    }

    private double calculateWeightError(List<BalanceMeasurement> measurements) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (BalanceMeasurement m : measurements) {
            if (m.getWeighingError() != null) {
                stats.addValue(m.getWeighingError().doubleValue());
            }
        }
        if (stats.getN() == 0) return 0.01;
        return stats.getStandardDeviation();
    }

    private String determineAccuracyGrade(double stdDev, List<BalanceMeasurement> measurements) {
        double avgMass = 10.0;
        if (!measurements.isEmpty() && measurements.get(0).getNominalMass() != null) {
            avgMass = measurements.get(0).getNominalMass().doubleValue();
        }

        double relativeError = stdDev / avgMass;

        if (relativeError <= 0.00001) return "特级";
        if (relativeError <= 0.0001) return "一级";
        if (relativeError <= 0.001) return "二级";
        if (relativeError <= 0.01) return "三级";
        return "等外";
    }

    private int[] calculateHistogram(double[] data, int bins) {
        if (data.length == 0) return new int[bins];

        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (double v : data) {
            if (v < min) min = v;
            if (v > max) max = v;
        }

        double range = max - min;
        if (range == 0) range = 1.0;

        int[] histogram = new int[bins];
        double binWidth = range / bins;

        for (double v : data) {
            int binIndex = (int) ((v - min) / binWidth);
            if (binIndex >= bins) binIndex = bins - 1;
            if (binIndex < 0) binIndex = 0;
            histogram[binIndex]++;
        }

        return histogram;
    }

    private List<BigDecimal> calculateHistogramBins(double min, double max, int bins) {
        List<BigDecimal> binList = new ArrayList<>();
        double range = max - min;
        if (range == 0) range = 1.0;
        double binWidth = range / bins;

        for (int i = 0; i < bins; i++) {
            binList.add(BigDecimal.valueOf(min + i * binWidth).setScale(6, RoundingMode.HALF_UP));
        }

        return binList;
    }

    private void saveAnalysisResult(Long balanceId, MonteCarloResult result) {
        ErrorAnalysis analysis = new ErrorAnalysis();
        analysis.setBalanceId(balanceId);
        analysis.setAnalysisTime(LocalDateTime.now());
        analysis.setSimulationCount(result.getSimulationCount());
        analysis.setMeanError(result.getMeanError());
        analysis.setStdDeviation(result.getStdDeviation());
        analysis.setCombinedUncertainty(result.getCombinedUncertainty());
        analysis.setExpandedUncertainty(result.getExpandedUncertainty());
        analysis.setCoverageFactor(result.getCoverageFactor());
        analysis.setFrictionContribution(result.getFrictionContribution());
        analysis.setArmLengthContribution(result.getArmLengthContribution());
        analysis.setWeightContribution(result.getWeightContribution());
        analysis.setAccuracyGrade(result.getAccuracyGrade());

        Map<String, Object> rawData = new HashMap<>();
        rawData.put("errorSamples", result.getErrorSamples());
        rawData.put("histogramBins", result.getHistogramBins());
        rawData.put("histogramCounts", result.getHistogramCounts());
        analysis.setRawData(rawData);

        errorAnalysisRepository.save(analysis);
    }

    public List<ErrorAnalysis> getAnalysisHistory(Long balanceId) {
        return errorAnalysisRepository.findByBalanceIdOrderByAnalysisTimeDesc(balanceId);
    }

    public ErrorAnalysis getLatestAnalysis(Long balanceId) {
        return errorAnalysisRepository.findLatestByBalanceId(balanceId).orElse(null);
    }
}
