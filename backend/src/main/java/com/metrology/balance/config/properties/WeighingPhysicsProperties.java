package com.metrology.balance.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.weighing-physics")
public class WeighingPhysicsProperties {

    private double defaultSwingAngleDeg = 5.0;

    private double fractureWearThresholdMm = 0.15;

    private double runningInWearMaxMm = 0.01;

    private double frictionJitterPct = 0.05;

    private double humidityAccelMultiplier = 100.0;

    private double agingPerHourFactor = 0.0001;

    private double highTempThreshold = 50.0;

    private double highTempSofteningPctPerDeg = 0.01;

    private double minHardnessFactor = 0.5;

    private double defaultTemperatureReference = 20.0;

    private double defaultSensorNominalMass = 10.0;

    private double defaultArmLengthMm = 150.0;
}
