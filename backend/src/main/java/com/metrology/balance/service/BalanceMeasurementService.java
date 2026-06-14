package com.metrology.balance.service;

import com.metrology.balance.dto.BalanceSensorData;
import com.metrology.balance.entity.Alert;
import com.metrology.balance.entity.Balance;
import com.metrology.balance.entity.BalanceMeasurement;
import com.metrology.balance.repository.AlertRepository;
import com.metrology.balance.repository.BalanceMeasurementRepository;
import com.metrology.balance.repository.BalanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceMeasurementService {

    private final BalanceRepository balanceRepository;
    private final BalanceMeasurementRepository measurementRepository;
    private final AlertRepository alertRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.alert.default-threshold:0.01}")
    private double defaultThreshold;

    @Value("${app.alert.warning-threshold:0.005}")
    private double warningThreshold;

    @Value("${websocket.topic:/topic/alerts}")
    private String alertTopic;

    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Transactional
    public BalanceMeasurement processSensorData(String payload) {
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

        BalanceMeasurement measurement = new BalanceMeasurement();
        measurement.setBalanceId(balance.getId());
        measurement.setMeasurementTime(
                sensorData.getTimestamp() != null ? sensorData.getTimestamp() : LocalDateTime.now());

        measurement.setNominalMass(sensorData.getNominalMass());
        measurement.setMeasuredMass(sensorData.getMeasuredMass());
        measurement.setWeighingError(sensorData.getWeighingError());
        measurement.setLeftArmLength(sensorData.getLeftArmLength());
        measurement.setRightArmLength(sensorData.getRightArmLength());
        measurement.setKnifeEdgeWearDepth(sensorData.getKnifeEdgeWearDepth());
        measurement.setKnifeEdgeFriction(sensorData.getKnifeEdgeFriction());
        measurement.setTemperature(sensorData.getTemperature());
        measurement.setHumidity(sensorData.getHumidity());

        if (sensorData.getNominalMass() != null && sensorData.getNominalMass().compareTo(BigDecimal.ZERO) > 0
                && sensorData.getWeighingError() != null) {
            BigDecimal relativeError = sensorData.getWeighingError()
                    .divide(sensorData.getNominalMass(), 8, RoundingMode.HALF_UP);
            measurement.setRelativeError(relativeError);
        }

        boolean isAlert = checkAlertCondition(balance, measurement);
        measurement.setIsAlert(isAlert);

        String alertLevel = null;
        if (isAlert) {
            alertLevel = determineAlertLevel(balance, measurement);
            measurement.setAlertLevel(alertLevel);
        }

        BalanceMeasurement savedMeasurement = measurementRepository.save(measurement);

        if (isAlert && alertLevel != null) {
            Alert alert = createAlert(balance, savedMeasurement, alertLevel);
            pushAlertToWebSocket(alert);
        }

        return savedMeasurement;
    }

    private boolean checkAlertCondition(Balance balance, BalanceMeasurement measurement) {
        if (measurement.getWeighingError() == null || measurement.getNominalMass() == null
                || measurement.getNominalMass().compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        BigDecimal threshold = balance.getAllowableError();
        if (threshold == null) {
            threshold = BigDecimal.valueOf(defaultThreshold);
        }

        BigDecimal relativeError = measurement.getWeighingError().abs()
                .divide(measurement.getNominalMass(), 8, RoundingMode.HALF_UP);

        return relativeError.compareTo(threshold) > 0;
    }

    private String determineAlertLevel(Balance balance, BalanceMeasurement measurement) {
        if (measurement.getWeighingError() == null || measurement.getNominalMass() == null
                || measurement.getNominalMass().compareTo(BigDecimal.ZERO) == 0) {
            return "INFO";
        }

        BigDecimal relativeError = measurement.getWeighingError().abs()
                .divide(measurement.getNominalMass(), 8, RoundingMode.HALF_UP);

        BigDecimal threshold = balance.getAllowableError();
        if (threshold == null) {
            threshold = BigDecimal.valueOf(defaultThreshold);
        }

        if (relativeError.compareTo(threshold.multiply(BigDecimal.valueOf(2))) > 0) {
            return "CRITICAL";
        } else if (relativeError.compareTo(threshold) > 0) {
            return "WARNING";
        }

        return "INFO";
    }

    private Alert createAlert(Balance balance, BalanceMeasurement measurement, String level) {
        Alert alert = new Alert();
        alert.setBalanceId(balance.getId());
        alert.setMeasurementId(measurement.getId());
        alert.setAlertType("WEIGHING_ERROR");
        alert.setAlertLevel(level);

        StringBuilder message = new StringBuilder();
        message.append("天平[").append(balance.getName()).append("]称量误差超标。");
        message.append("标称质量:").append(measurement.getNominalMass()).append("g, ");
        message.append("测量误差:").append(measurement.getWeighingError()).append("g");

        if (measurement.getRelativeError() != null) {
            message.append(", 相对误差:").append(measurement.getRelativeError()
                    .multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP)).append("%");
        }

        alert.setMessage(message.toString());
        alert.setThresholdValue(balance.getAllowableError());
        alert.setActualValue(measurement.getRelativeError());

        return alertRepository.save(alert);
    }

    private void pushAlertToWebSocket(Alert alert) {
        try {
            messagingTemplate.convertAndSend(alertTopic, alert);
            log.debug("告警已推送到WebSocket: {}", alert.getId());
        } catch (Exception e) {
            log.warn("推送告警到WebSocket失败: {}", e.getMessage());
        }
    }

    public List<BalanceMeasurement> getMeasurements(Long balanceId, LocalDateTime startTime, LocalDateTime endTime) {
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
