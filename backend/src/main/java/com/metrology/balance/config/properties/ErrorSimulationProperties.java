package com.metrology.balance.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.error-simulation")
public class ErrorSimulationProperties {

    private int defaultSimulationCount = 100000;

    private int maxSimulationCount = 500000;

    private double coverageFactor = 2.0;

    private int histogramBins = 50;

    private int errorSamplePoints = 1000;

    private double humidityBiasPerPctPerGram = 0.00001;

    private double temperatureBiasPerDegPerGram = 0.000005;

    private double defaultAvgTemperature = 20.0;

    private double defaultAvgHumidity = 50.0;

    private double defaultTemperatureStd = 5.0;

    private double defaultHumidityStd = 15.0;

    private double wearProgressFactor = 0.5;

    private double minTemperature = -10.0;

    private double maxTemperature = 60.0;

    private double defaultSensorNominalMass = 10.0;
}
