package com.metrology.balance.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 刀口磨损物理模型
 *
 * 基于Archard磨损定律 + 摩擦学经验公式:
 * 1. 磨损深度 h = k × P × S / H  (k为磨损系数, P为载荷, S为滑动距离, H为硬度)
 * 2. 摩擦系数 μ(h) = μ₀ + α × h^β + γ × h × T
 *    其中α,β,γ为材料常数, T为环境温度影响因子
 * 3. 湿度影响: μ_rust = μ × (1 + δ × RH^2)  (锈蚀效应)
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnifeEdgeWearModel {

    private static final double DEFAULT_INITIAL_FRICTION = 0.001;
    private static final double DEFAULT_WEAR_COEFFICIENT = 1.5e-8;
    private static final double DEFAULT_HARDNESS = 180.0;
    private static final double FRACTURE_WEAR_THRESHOLD = 0.15;
    private static final double DEFAULT_ALPHA = 0.08;
    private static final double DEFAULT_BETA = 0.65;
    private static final double DEFAULT_GAMMA = 0.002;
    private static final double DEFAULT_DELTA = 0.00008;

    private double initialFrictionCoefficient;
    private double wearCoefficient;
    private double materialHardness;
    private double alpha;
    private double beta;
    private double gamma;
    private double delta;

    private Double accumulatedWearDepth;
    private Long totalUsageCount;
    private LocalDateTime firstUsageTime;

    public static KnifeEdgeWearModel createDefault() {
        return KnifeEdgeWearModel.builder()
                .initialFrictionCoefficient(DEFAULT_INITIAL_FRICTION)
                .wearCoefficient(DEFAULT_WEAR_COEFFICIENT)
                .materialHardness(DEFAULT_HARDNESS)
                .alpha(DEFAULT_ALPHA)
                .beta(DEFAULT_BETA)
                .gamma(DEFAULT_GAMMA)
                .delta(DEFAULT_DELTA)
                .accumulatedWearDepth(0.0)
                .totalUsageCount(0L)
                .firstUsageTime(null)
                .build();
    }

    public static KnifeEdgeWearModel createWithMaterial(String material) {
        KnifeEdgeWearModel model = createDefault();
        switch (material == null ? "" : material.toLowerCase()) {
            case "青铜":
            case "bronze":
                model.setMaterialHardness(150.0);
                model.setInitialFrictionCoefficient(0.0012);
                model.setAlpha(0.09);
                break;
            case "铁":
            case "steel":
            case "iron":
                model.setMaterialHardness(200.0);
                model.setInitialFrictionCoefficient(0.0015);
                model.setAlpha(0.07);
                model.setDelta(0.00012);
                break;
            case "钢":
                model.setMaterialHardness(350.0);
                model.setInitialFrictionCoefficient(0.0008);
                model.setAlpha(0.05);
                break;
            case "玉石":
            case "jade":
                model.setMaterialHardness(500.0);
                model.setInitialFrictionCoefficient(0.0005);
                model.setAlpha(0.03);
                break;
            case "玛瑙":
            case "agate":
                model.setMaterialHardness(650.0);
                model.setInitialFrictionCoefficient(0.0004);
                model.setAlpha(0.02);
                break;
            default:
                break;
        }
        return model;
    }

    /**
     * 根据当前状态和环境参数计算实时摩擦系数
     *
     * @param nominalMass  标称质量 (g) - 影响载荷P
     * @param temperature  环境温度 (℃)
     * @param humidity     环境湿度 (%)
     * @param armLength    臂长 (mm) - 影响滑动距离S
     * @return 当前摩擦系数
     */
    public double calculateDynamicFriction(double nominalMass,
                                           double temperature,
                                           double humidity,
                                           double armLength) {
        if (accumulatedWearDepth == null) {
            accumulatedWearDepth = 0.0;
        }

        double h = accumulatedWearDepth;

        double mu_0 = initialFrictionCoefficient;

        double mu_wear = mu_0 + alpha * Math.pow(h, beta) + gamma * h * temperatureEffect(temperature);

        double mu_final = mu_wear * (1.0 + delta * Math.pow(humidity / 100.0, 2));

        if (h > FRACTURE_WEAR_THRESHOLD) {
            double fractureFactor = 1.0 + 2.5 * Math.pow(h - FRACTURE_WEAR_THRESHOLD, 1.5);
            mu_final *= fractureFactor;
            log.debug("刀口已进入疲劳磨损阶段, h={}mm, 摩擦放大系数={}", h, fractureFactor);
        }

        double jitter = (Math.random() - 0.5) * 2.0 * mu_final * 0.05;

        return Math.max(0.0001, mu_final + jitter);
    }

    /**
     * 记录一次使用，更新累计磨损深度
     * 基于修正的Archard磨损定律:
     *   Δh = k × P × S / (H × √A)
     *   A为名义接触面积
     */
    public double recordUsage(double nominalMass,
                              double armLength,
                              double swingAngle,
                              double temperature,
                              double humidity) {
        if (totalUsageCount == null) {
            totalUsageCount = 0L;
        }
        if (accumulatedWearDepth == null) {
            accumulatedWearDepth = 0.0;
        }
        if (firstUsageTime == null) {
            firstUsageTime = LocalDateTime.now();
        }

        double P = nominalMass * 9.81 / 1000.0;
        double S = 2.0 * Math.PI * armLength * (swingAngle / 360.0) * 3.0;

        double contactArea = Math.PI * Math.pow(Math.max(0.5, accumulatedWearDepth + 0.5), 2);

        double hardnessFactor = 1.0;
        if (temperature > 50.0) {
            hardnessFactor = Math.max(0.5, 1.0 - (temperature - 50.0) * 0.01);
        }

        double deltaH = wearCoefficient * P * S / (materialHardness * hardnessFactor * Math.sqrt(contactArea));

        double humidityAccel = 1.0 + delta * Math.pow(humidity / 100.0, 2.5) * 100;
        deltaH *= humidityAccel;

        long hoursElapsed = firstUsageTime != null
                ? Duration.between(firstUsageTime, LocalDateTime.now()).toHours()
                : 0;
        double agingFactor = 1.0 + hoursElapsed * 0.0001;
        deltaH *= agingFactor;

        accumulatedWearDepth += deltaH;
        totalUsageCount++;

        return deltaH;
    }

    private double temperatureEffect(double temperature) {
        double deltaT = temperature - 20.0;
        return Math.exp(Math.abs(deltaT) * 0.015) - 1.0;
    }

    /**
     * 获取当前磨损阶段
     */
    public WearStage getCurrentWearStage() {
        if (accumulatedWearDepth == null) return WearStage.RUNNING_IN;
        double h = accumulatedWearDepth;
        if (h < 0.01) return WearStage.RUNNING_IN;
        if (h < FRACTURE_WEAR_THRESHOLD) return WearStage.STEADY;
        return WearStage.SEVERE;
    }

    /**
     * 获取磨损状态报告
     */
    public WearReport getWearReport() {
        return WearReport.builder()
                .accumulatedWearDepth(BigDecimal.valueOf(accumulatedWearDepth == null ? 0 : accumulatedWearDepth)
                        .setScale(6, RoundingMode.HALF_UP))
                .totalUsageCount(totalUsageCount == null ? 0 : totalUsageCount)
                .wearStage(getCurrentWearStage().name())
                .wearStageDescription(getCurrentWearStage().getDescription())
                .build();
    }

    public enum WearStage {
        RUNNING_IN("跑合阶段 - 摩擦系数下降"),
        STEADY("稳定磨损阶段 - 摩擦系数稳定"),
        SEVERE("剧烈磨损阶段 - 摩擦系数急剧上升");

        private final String description;

        WearStage(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WearReport {
        private BigDecimal accumulatedWearDepth;
        private Long totalUsageCount;
        private String wearStage;
        private String wearStageDescription;
    }
}
