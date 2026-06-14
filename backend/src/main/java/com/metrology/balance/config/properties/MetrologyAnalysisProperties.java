package com.metrology.balance.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.metrology")
public class MetrologyAnalysisProperties {

    private int defaultKMeansIterations = 100;

    private int defaultMaxClusters = 10;

    private int hardMaxClusters = 8;

    private int minSampleCount = 3;

    private double jinToLiangRatio = 16.0;

    private double sampleCountPenaltyFactor = 0.5;

    private double maxPriorMatchScore = 4.0;

    private int maxLiangMultipleCheck = 32;

    private int maxJinMultipleCheck = 4;
}
