package com.metrology.balance.modules.error_simulator;

import com.metrology.balance.config.properties.ErrorSimulationProperties;
import com.metrology.balance.dto.MonteCarloResult;
import com.metrology.balance.entity.Balance;
import com.metrology.balance.entity.BalanceMeasurement;
import com.metrology.balance.entity.ErrorAnalysis;
import com.metrology.balance.event.ErrorAnalysisCompletedEvent;
import com.metrology.balance.model.KnifeEdgeWearModel;
import com.metrology.balance.repository.BalanceMeasurementRepository;
import com.metrology.balance.repository.BalanceRepository;
import com.metrology.balance.repository.ErrorAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.context.ApplicationEventPublisher;
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
public class ErrorSimulatorService {

    private final BalanceRepository balanceRepository;
    private final BalanceMeasurementRepository measurementRepository;
    private final ErrorAnalysisRepository errorAnalysisRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ErrorSimulationProperties simProps;

    @Transactional
    public MonteCarloResult runMonteCarloSimulation(Long balanceId, int requestedCount) {
        Balance balance = balanceRepository.findById(balanceId)
                .orElseThrow(() -> new IllegalArgumentException("天平不存在: " + balanceId));

        List<BalanceMeasurement> measurements = measurementRepository
                .findByBalanceIdOrderByMeasurementTimeDesc(balanceId);

        if (measurements.isEmpty()) {
            throw new IllegalStateException("该天平暂无测量数据");
        }

        int count = normalizeCount(requestedCount);

        double[] errors = simulateErrors(balance, measurements, count);

        DescriptiveStatistics stats = new DescriptiveStatistics(errors);
        double meanError = stats.getMean();
        double stdDev = stats.getStandardDeviation();

        double frictionError = calcFrictionError(measurements);
        double armLengthError = calcArmLengthError(measurements);
        double weightError = calcWeightError(measurements);

        double totalUncertainty = Math.sqrt(
                frictionError * frictionError
                        + armLengthError * armLengthError
                        + weightError * weightError);

        double frictionContrib = pct(frictionError, totalUncertainty);
        double armLengthContrib = pct(armLengthError, totalUncertainty);
        double weightContrib = pct(weightError, totalUncertainty);

        String accuracyGrade = determineAccuracyGrade(stdDev, measurements);

        int[] histogramCounts = buildHistogram(errors, simProps.getHistogramBins());
        List<BigDecimal> histogramBins = buildHistogramBins(stats.getMin(), stats.getMax(),
                simProps.getHistogramBins());
        List<Integer> histogramCountList = new ArrayList<>();
        for (int hc : histogramCounts) histogramCountList.add(hc);

        List<BigDecimal> errorSamples = new ArrayList<>();
        int sampleStep = Math.max(1, count / simProps.getErrorSamplePoints());
        for (int i = 0; i < count; i += sampleStep) {
            errorSamples.add(BigDecimal.valueOf(errors[i]).setScale(8, RoundingMode.HALF_UP));
        }

        MonteCarloResult result = MonteCarloResult.builder()
                .simulationCount(count)
                .meanError(bd(meanError, 8))
                .stdDeviation(bd(stdDev, 8))
                .combinedUncertainty(bd(stdDev, 8))
                .expandedUncertainty(bd(stdDev * simProps.getCoverageFactor(), 8))
                .coverageFactor(bd(simProps.getCoverageFactor()))
                .frictionContribution(bd(frictionContrib, 2))
                .armLengthContribution(bd(armLengthContrib, 2))
                .weightContribution(bd(weightContrib, 2))
                .accuracyGrade(accuracyGrade)
                .errorSamples(errorSamples)
                .histogramBins(histogramBins)
                .histogramCounts(histogramCountList)
                .build();

        saveAnalysisResult(balanceId, result);

        eventPublisher.publishEvent(new ErrorAnalysisCompletedEvent(this, balanceId, result));

        return result;
    }

    private int normalizeCount(int requestedCount) {
        if (requestedCount <= 0) return simProps.getDefaultSimulationCount();
        return Math.min(requestedCount, simProps.getMaxSimulationCount());
    }

    private double[] simulateErrors(Balance balance,
                                     List<BalanceMeasurement> measurements,
                                     int count) {
        double[] errors = new double[count];

        KnifeEdgeWearModel wearModel = KnifeEdgeWearModel.createWithMaterial(balance.getMaterial());

        SimulationStats s = collectStats(measurements);
        wearModel.setAccumulatedWearDepth(s.totalWearDepth);
        wearModel.setTotalUsageCount(s.totalCount);
        if (!measurements.isEmpty()) {
            wearModel.setFirstUsageTime(measurements.get(measurements.size() - 1).getMeasurementTime());
        }

        NormalDistribution armDiffDist = new NormalDistribution(s.armDiffMean, s.armDiffStd);
        NormalDistribution weightErrorDist = new NormalDistribution(s.weightErrorMean, s.weightErrorStd);
        NormalDistribution tempDist = new NormalDistribution(s.avgTemperature, s.avgTemperatureVar);
        NormalDistribution humidityDist = new NormalDistribution(s.avgHumidity, s.avgHumidityVar);

        double avgNominalMass = s.avgNominalMass;
        double avgArmLength = s.avgArmLength;

        log.info("天平[{}]磨损状态: {}, 累计磨损深度={}mm, 使用次数={}",
                balance.getBalanceCode(),
                wearModel.getWearReport().getWearStage(),
                wearModel.getWearReport().getAccumulatedWearDepth(),
                wearModel.getWearReport().getTotalUsageCount());

        for (int i = 0; i < count; i++) {
            double progressRatio = (double) i / count;
            double simulatedWear = s.totalWearDepth * (1.0 + progressRatio * simProps.getWearProgressFactor());
            wearModel.setAccumulatedWearDepth(simulatedWear);

            double temperature = clamp(tempDist.sample(),
                    simProps.getMinTemperature(), simProps.getMaxTemperature());
            double humidity = clamp(humidityDist.sample(), 0, 100);

            double dynamicFriction = wearModel.calculateDynamicFriction(
                    avgNominalMass, temperature, humidity, avgArmLength);

            double armDiff = armDiffDist.sample();
            double weightErr = weightErrorDist.sample();

            double armLengthRatio = armDiff / avgArmLength;
            double armLengthError = avgNominalMass * armLengthRatio;
            double frictionError = dynamicFriction * avgNominalMass;

            double humidityBias = (humidity - 50.0) * simProps.getHumidityBiasPerPctPerGram() * avgNominalMass;
            double tempBias = (temperature - 20.0) * simProps.getTemperatureBiasPerDegPerGram() * avgNominalMass;

            errors[i] = weightErr + armLengthError + frictionError + humidityBias + tempBias;
        }

        return errors;
    }

    private SimulationStats collectStats(List<BalanceMeasurement> measurements) {
        SimulationStats s = new SimulationStats();
        s.avgTemperature = simProps.getDefaultAvgTemperature();
        s.avgHumidity = simProps.getDefaultAvgHumidity();
        s.avgTemperatureVar = simProps.getDefaultTemperatureStd();
        s.avgHumidityVar = simProps.getDefaultHumidityStd();
        s.avgNominalMass = simProps.getDefaultSensorNominalMass();
        s.avgArmLength = 150.0;

        DescriptiveStatistics armLengthDiffStats = new DescriptiveStatistics();
        DescriptiveStatistics weightErrorStats = new DescriptiveStatistics();

        for (BalanceMeasurement m : measurements) {
            if (m.getKnifeEdgeWearDepth() != null) {
                s.totalWearDepth = Math.max(s.totalWearDepth, m.getKnifeEdgeWearDepth().doubleValue());
            }
            s.totalCount = Math.max(s.totalCount, measurements.size());
            if (m.getTemperature() != null) s.avgTemperature = m.getTemperature().doubleValue();
            if (m.getHumidity() != null) s.avgHumidity = m.getHumidity().doubleValue();
            if (m.getLeftArmLength() != null && m.getRightArmLength() != null) {
                armLengthDiffStats.addValue(m.getLeftArmLength().doubleValue()
                        - m.getRightArmLength().doubleValue());
            }
            if (m.getWeighingError() != null) {
                weightErrorStats.addValue(m.getWeighingError().doubleValue());
            }
            if (m.getNominalMass() != null) s.avgNominalMass = m.getNominalMass().doubleValue();
        }

        if (!measurements.isEmpty() && measurements.get(0).getNominalMass() != null) {
            s.avgNominalMass = measurements.get(0).getNominalMass().doubleValue();
        }

        s.armDiffMean = armLengthDiffStats.getN() > 0 ? armLengthDiffStats.getMean() : 0.0;
        s.armDiffStd = armLengthDiffStats.getN() > 1 ? armLengthDiffStats.getStandardDeviation() : 0.5;
        s.weightErrorMean = weightErrorStats.getN() > 0 ? weightErrorStats.getMean() : 0.0;
        s.weightErrorStd = weightErrorStats.getN() > 1 ? weightErrorStats.getStandardDeviation() : 0.01;

        return s;
    }

    private double calcFrictionError(List<BalanceMeasurement> measurements) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (BalanceMeasurement m : measurements) {
            if (m.getKnifeEdgeFriction() != null) {
                stats.addValue(m.getKnifeEdgeFriction().doubleValue());
            }
        }
        return stats.getN() == 0 ? 0.001 : stats.getStandardDeviation();
    }

    private double calcArmLengthError(List<BalanceMeasurement> measurements) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (BalanceMeasurement m : measurements) {
            if (m.getLeftArmLength() != null && m.getRightArmLength() != null
                    && m.getNominalMass() != null) {
                double ratio = (m.getLeftArmLength().doubleValue() - m.getRightArmLength().doubleValue())
                        / m.getLeftArmLength().doubleValue();
                stats.addValue(ratio * m.getNominalMass().doubleValue());
            }
        }
        return stats.getN() == 0 ? 10.0 * 0.001 : stats.getStandardDeviation();
    }

    private double calcWeightError(List<BalanceMeasurement> measurements) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (BalanceMeasurement m : measurements) {
            if (m.getWeighingError() != null) {
                stats.addValue(m.getWeighingError().doubleValue());
            }
        }
        return stats.getN() == 0 ? 0.01 : stats.getStandardDeviation();
    }

    private String determineAccuracyGrade(double stdDev, List<BalanceMeasurement> measurements) {
        double avgMass = simProps.getDefaultSensorNominalMass();
        if (!measurements.isEmpty() && measurements.get(0).getNominalMass() != null) {
            avgMass = measurements.get(0).getNominalMass().doubleValue();
        }
        double rel = stdDev / avgMass;
        if (rel <= 0.00001) return "特级";
        if (rel <= 0.0001) return "一级";
        if (rel <= 0.001) return "二级";
        if (rel <= 0.01) return "三级";
        return "等外";
    }

    private int[] buildHistogram(double[] data, int bins) {
        if (data.length == 0) return new int[bins];
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double v : data) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        double range = max - min == 0 ? 1.0 : max - min;
        int[] histogram = new int[bins];
        double binWidth = range / bins;
        for (double v : data) {
            int idx = (int) ((v - min) / binWidth);
            if (idx >= bins) idx = bins - 1;
            if (idx < 0) idx = 0;
            histogram[idx]++;
        }
        return histogram;
    }

    private List<BigDecimal> buildHistogramBins(double min, double max, int bins) {
        List<BigDecimal> list = new ArrayList<>();
        double range = max - min == 0 ? 1.0 : max - min;
        double binWidth = range / bins;
        for (int i = 0; i < bins; i++) {
            list.add(bd(min + i * binWidth, 6));
        }
        return list;
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

    private static BigDecimal bd(double v) { return BigDecimal.valueOf(v); }
    private static BigDecimal bd(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }
    private static double pct(double err, double total) {
        return (err * err) / (total * total) * 100;
    }
    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static class SimulationStats {
        double totalWearDepth = 0;
        long totalCount = 0;
        double avgTemperature;
        double avgHumidity;
        double avgTemperatureVar;
        double avgHumidityVar;
        double avgNominalMass;
        double avgArmLength;
        double armDiffMean;
        double armDiffStd;
        double weightErrorMean;
        double weightErrorStd;
    }
}
