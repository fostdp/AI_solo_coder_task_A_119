package com.metrology.balance.modules.mqtt_receiver;

import com.metrology.balance.config.properties.AlertProperties;
import com.metrology.balance.config.properties.WeighingPhysicsProperties;
import com.metrology.balance.dto.BalanceSensorData;
import com.metrology.balance.entity.Alert;
import com.metrology.balance.entity.Balance;
import com.metrology.balance.entity.BalanceMeasurement;
import com.metrology.balance.event.AlertTriggeredEvent;
import com.metrology.balance.event.MeasurementSavedEvent;
import com.metrology.balance.model.KnifeEdgeWearModel;
import com.metrology.balance.repository.AlertRepository;
import com.metrology.balance.repository.BalanceMeasurementRepository;
import com.metrology.balance.repository.BalanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttReceiverService {

    private final BalanceRepository balanceRepository;
    private final BalanceMeasurementRepository measurementRepository;
    private final AlertRepository alertRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AlertProperties alertProps;
    private final WeighingPhysicsProperties physicsProps;

    private final ConcurrentHashMap<String, KnifeEdgeWearModel> wearModelCache = new ConcurrentHashMap<>();

    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    public BalanceMeasurement processRawPayload(String payload) {
        try {
            BalanceSensorData sensorData = objectMapper.readValue(payload, BalanceSensorData.class);
            return processSensorData(sensorData);
        } catch (Exception e) {
            log.error("解析传感器数据失败: {}", e.getMessage());
            throw new IllegalArgumentException("无效的传感器数据格式", e);
        }
    }

    @Transactional
    public BalanceMeasurement processSensorData(BalanceSensorData sensorData) {
        Balance balance = balanceRepository.findByBalanceCode(sensorData.getBalanceCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "天平编码不存在: " + sensorData.getBalanceCode()));

        BalanceMeasurement measurement = buildMeasurement(sensorData, balance);

        KnifeEdgeWearModel wearModel = getOrInitWearModel(balance);
        applyWearAndCalibration(measurement, sensorData, wearModel, balance);
        computeRelativeError(measurement, sensorData);

        boolean isAlert = checkAlertCondition(balance, measurement);
        measurement.setIsAlert(isAlert);

        String alertLevel = null;
        if (isAlert) {
            alertLevel = determineAlertLevel(balance, measurement);
            measurement.setAlertLevel(alertLevel);
        }

        BalanceMeasurement saved = measurementRepository.save(measurement);

        eventPublisher.publishEvent(new MeasurementSavedEvent(
                this, saved, balance.getBalanceCode(), isAlert, alertLevel));

        if (isAlert && alertLevel != null) {
            Alert alert = createAndSaveAlert(balance, saved, alertLevel);
            eventPublisher.publishEvent(new AlertTriggeredEvent(this, alert, balance.getBalanceCode()));
        }

        return saved;
    }

    private BalanceMeasurement buildMeasurement(BalanceSensorData sensorData, Balance balance) {
        BalanceMeasurement m = new BalanceMeasurement();
        m.setBalanceId(balance.getId());
        m.setMeasurementTime(sensorData.getTimestamp() != null
                ? sensorData.getTimestamp() : LocalDateTime.now());
        m.setNominalMass(sensorData.getNominalMass());
        m.setMeasuredMass(sensorData.getMeasuredMass());
        m.setWeighingError(sensorData.getWeighingError());
        m.setLeftArmLength(sensorData.getLeftArmLength());
        m.setRightArmLength(sensorData.getRightArmLength());
        m.setKnifeEdgeWearDepth(sensorData.getKnifeEdgeWearDepth());
        m.setTemperature(sensorData.getTemperature());
        m.setHumidity(sensorData.getHumidity());
        return m;
    }

    private KnifeEdgeWearModel getOrInitWearModel(Balance balance) {
        return wearModelCache.computeIfAbsent(balance.getBalanceCode(), k -> {
            KnifeEdgeWearModel m = KnifeEdgeWearModel.createWithMaterial(balance.getMaterial());
            List<BalanceMeasurement> hist = measurementRepository
                    .findByBalanceIdOrderByMeasurementTimeDesc(balance.getId());
            if (!hist.isEmpty()) {
                double maxWear = hist.stream()
                        .filter(h -> h.getKnifeEdgeWearDepth() != null)
                        .mapToDouble(h -> h.getKnifeEdgeWearDepth().doubleValue())
                        .max().orElse(0);
                m.setAccumulatedWearDepth(maxWear);
                m.setTotalUsageCount((long) hist.size());
                m.setFirstUsageTime(hist.get(hist.size() - 1).getMeasurementTime());
            }
            return m;
        });
    }

    private void applyWearAndCalibration(BalanceMeasurement measurement,
                                          BalanceSensorData sensorData,
                                          KnifeEdgeWearModel wearModel,
                                          Balance balance) {
        double nominalMass = valueOr(sensorData.getNominalMass(), 10.0);
        double temperature = valueOr(sensorData.getTemperature(), physicsProps.getDefaultTemperatureReference());
        double humidity = valueOr(sensorData.getHumidity(), 50.0);
        double armLength = valueOr(sensorData.getLeftArmLength(), 150.0);

        if (sensorData.getKnifeEdgeWearDepth() != null) {
            wearModel.setAccumulatedWearDepth(sensorData.getKnifeEdgeWearDepth().doubleValue());
        }

        wearModel.recordUsage(nominalMass, armLength, physicsProps.getDefaultSwingAngleDeg(),
                temperature, humidity);

        double expectedFriction = wearModel.calculateDynamicFriction(
                nominalMass, temperature, humidity, armLength);

        double reportedFriction = sensorData.getKnifeEdgeFriction() != null
                ? sensorData.getKnifeEdgeFriction().doubleValue() : expectedFriction;

        double deviation = Math.abs(reportedFriction - expectedFriction) / expectedFriction;
        if (deviation > alertProps.getFrictionDeviationWarnPct()) {
            log.warn("天平[{}]摩擦系数偏差过大: 报告={}, 模型预期={}, 偏差={}%",
                    balance.getBalanceCode(),
                    String.format("%.6f", reportedFriction),
                    String.format("%.6f", expectedFriction),
                    String.format("%.1f", deviation * 100));
        }

        double calibrated = alertProps.getSensorWeight() * reportedFriction
                + alertProps.getModelWeight() * expectedFriction;
        measurement.setKnifeEdgeFriction(BigDecimal.valueOf(calibrated)
                .setScale(6, RoundingMode.HALF_UP));

        if (sensorData.getKnifeEdgeWearDepth() == null) {
            measurement.setKnifeEdgeWearDepth(
                    BigDecimal.valueOf(wearModel.getAccumulatedWearDepth() == null
                            ? 0.0 : wearModel.getAccumulatedWearDepth())
                            .setScale(6, RoundingMode.HALF_UP));
        }

        KnifeEdgeWearModel.WearReport report = wearModel.getWearReport();
        if (KnifeEdgeWearModel.WearStage.SEVERE.name().equals(report.getWearStage())) {
            log.warn("天平[{}]进入剧烈磨损阶段: {}", balance.getBalanceCode(),
                    report.getWearStageDescription());
        }
    }

    private void computeRelativeError(BalanceMeasurement measurement, BalanceSensorData sensorData) {
        if (sensorData.getNominalMass() != null
                && sensorData.getNominalMass().compareTo(BigDecimal.ZERO) > 0
                && sensorData.getWeighingError() != null) {
            BigDecimal rel = sensorData.getWeighingError()
                    .divide(sensorData.getNominalMass(), 8, RoundingMode.HALF_UP);
            measurement.setRelativeError(rel);
        }
    }

    private boolean checkAlertCondition(Balance balance, BalanceMeasurement measurement) {
        if (measurement.getWeighingError() == null || measurement.getNominalMass() == null
                || measurement.getNominalMass().compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }
        BigDecimal threshold = balance.getAllowableError() != null
                ? balance.getAllowableError() : BigDecimal.valueOf(alertProps.getDefaultThreshold());
        BigDecimal relativeError = measurement.getWeighingError().abs()
                .divide(measurement.getNominalMass(), 8, RoundingMode.HALF_UP);
        return relativeError.compareTo(threshold) > 0;
    }

    private String determineAlertLevel(Balance balance, BalanceMeasurement measurement) {
        if (measurement.getWeighingError() == null || measurement.getNominalMass() == null
                || measurement.getNominalMass().compareTo(BigDecimal.ZERO) == 0) {
            return "INFO";
        }
        BigDecimal threshold = balance.getAllowableError() != null
                ? balance.getAllowableError() : BigDecimal.valueOf(alertProps.getDefaultThreshold());
        BigDecimal relativeError = measurement.getWeighingError().abs()
                .divide(measurement.getNominalMass(), 8, RoundingMode.HALF_UP);
        if (relativeError.compareTo(
                threshold.multiply(BigDecimal.valueOf(alertProps.getCriticalMultiplier()))) > 0) {
            return "CRITICAL";
        } else if (relativeError.compareTo(threshold) > 0) {
            return "WARNING";
        }
        return "INFO";
    }

    private Alert createAndSaveAlert(Balance balance, BalanceMeasurement measurement, String level) {
        Alert alert = new Alert();
        alert.setBalanceId(balance.getId());
        alert.setMeasurementId(measurement.getId());
        alert.setAlertType("WEIGHING_ERROR");
        alert.setAlertLevel(level);

        StringBuilder sb = new StringBuilder();
        sb.append("天平[").append(balance.getName()).append("]称量误差超标。");
        sb.append("标称质量:").append(measurement.getNominalMass()).append("g, ");
        sb.append("测量误差:").append(measurement.getWeighingError()).append("g");
        if (measurement.getRelativeError() != null) {
            sb.append(", 相对误差:").append(measurement.getRelativeError()
                    .multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP)).append("%");
        }
        alert.setMessage(sb.toString());
        alert.setThresholdValue(balance.getAllowableError());
        alert.setActualValue(measurement.getRelativeError());
        return alertRepository.save(alert);
    }

    private static double valueOr(BigDecimal value, double fallback) {
        return value != null ? value.doubleValue() : fallback;
    }

    public List<BalanceMeasurement> getMeasurements(Long balanceId,
                                                     LocalDateTime startTime,
                                                     LocalDateTime endTime) {
        if (startTime != null && endTime != null) {
            return measurementRepository
                    .findByBalanceIdAndMeasurementTimeBetweenOrderByMeasurementTime(
                            balanceId, startTime, endTime);
        }
        return measurementRepository.findByBalanceIdOrderByMeasurementTimeDesc(balanceId);
    }

    public List<BalanceMeasurement> getLatestMeasurements(int limit) {
        return measurementRepository.findLatestForEachBalance();
    }
}
