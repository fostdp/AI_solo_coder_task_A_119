package com.metrology.balance.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.alert")
public class AlertProperties {

    private double defaultThreshold = 0.01;

    private double warningThreshold = 0.005;

    private double criticalMultiplier = 2.0;

    private double frictionDeviationWarnPct = 0.30;

    private double sensorWeight = 0.7;

    private double modelWeight = 0.3;
}
