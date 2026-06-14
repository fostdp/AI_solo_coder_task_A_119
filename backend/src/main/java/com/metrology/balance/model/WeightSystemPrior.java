package com.metrology.balance.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 古代权衡制度先验知识库
 *
 * 基于《中国科学技术史·度量衡卷》、《历代度量衡考》等文献
 * 整理的各朝代斤/两标准克数先验值
 *
 * 先验形式: P(θ) ~ N(μ_prior, σ_prior²)
 * 后验: P(θ|data) ∝ P(data|θ) · P(θ)
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeightSystemPrior {

    private String dynastyName;
    private Integer dynastyId;

    private double priorJinStandard;
    private double priorJinStd;

    private double priorLiangStandard;
    private double priorLiangStd;

    private double priorJinToLiangRatio;
    private double ratioConfidence;

    private String source;
    private double credibility;

    private static final Map<Integer, WeightSystemPrior> PRIOR_KNOWLEDGE = new HashMap<>();

    static {
        PRIOR_KNOWLEDGE.put(1, WeightSystemPrior.builder()
                .dynastyId(1).dynastyName("战国")
                .priorJinStandard(250.0).priorJinStd(15.0)
                .priorLiangStandard(15.625).priorLiangStd(0.8)
                .priorJinToLiangRatio(16.0).ratioConfidence(0.95)
                .source("战国秦、楚、齐等国出土权器综合").credibility(0.85)
                .build());

        PRIOR_KNOWLEDGE.put(2, WeightSystemPrior.builder()
                .dynastyId(2).dynastyName("秦")
                .priorJinStandard(253.0).priorJinStd(8.0)
                .priorLiangStandard(15.8125).priorLiangStd(0.5)
                .priorJinToLiangRatio(16.0).ratioConfidence(0.98)
                .source("商鞅方升、秦权统一制度").credibility(0.95)
                .build());

        PRIOR_KNOWLEDGE.put(3, WeightSystemPrior.builder()
                .dynastyId(3).dynastyName("西汉")
                .priorJinStandard(248.0).priorJinStd(10.0)
                .priorLiangStandard(15.5).priorLiangStd(0.6)
                .priorJinToLiangRatio(16.0).ratioConfidence(0.97)
                .source("满城汉墓、马王堆汉墓出土权器").credibility(0.92)
                .build());

        PRIOR_KNOWLEDGE.put(4, WeightSystemPrior.builder()
                .dynastyId(4).dynastyName("东汉")
                .priorJinStandard(222.0).priorJinStd(12.0)
                .priorLiangStandard(13.875).priorLiangStd(0.7)
                .priorJinToLiangRatio(16.0).ratioConfidence(0.95)
                .source("东汉铜权、光和大司农铜权").credibility(0.88)
                .build());

        PRIOR_KNOWLEDGE.put(5, WeightSystemPrior.builder()
                .dynastyId(5).dynastyName("三国")
                .priorJinStandard(220.0).priorJinStd(15.0)
                .priorLiangStandard(13.75).priorLiangStd(0.9)
                .priorJinToLiangRatio(16.0).ratioConfidence(0.90)
                .source("魏、蜀、吴权器存世较少，承东汉制").credibility(0.75)
                .build());

        PRIOR_KNOWLEDGE.put(6, WeightSystemPrior.builder()
                .dynastyId(6).dynastyName("西晋")
                .priorJinStandard(220.0).priorJinStd(15.0)
                .priorLiangStandard(13.75).priorLiangStd(0.9)
                .priorJinToLiangRatio(16.0).ratioConfidence(0.90)
                .source("承三国制度").credibility(0.70)
                .build());

        PRIOR_KNOWLEDGE.put(7, WeightSystemPrior.builder()
                .dynastyId(7).dynastyName("东晋")
                .priorJinStandard(220.0).priorJinStd(18.0)
                .priorLiangStandard(13.75).priorLiangStd(1.1)
                .priorJinToLiangRatio(16.0).ratioConfidence(0.85)
                .source("南渡后制度混乱，存疑较多").credibility(0.60)
                .build());

        PRIOR_KNOWLEDGE.put(8, WeightSystemPrior.builder()
                .dynastyId(8).dynastyName("南北朝")
                .priorJinStandard(450.0).priorJinStd(80.0)
                .priorLiangStandard(28.125).priorLiangStd(5.0)
                .priorJinToLiangRatio(16.0).ratioConfidence(0.80)
                .source("南北朝度量衡混乱，南制约220g，北制约500g").credibility(0.55)
                .build());

        PRIOR_KNOWLEDGE.put(9, WeightSystemPrior.builder()
                .dynastyId(9).dynastyName("隋")
                .priorJinStandard(660.0).priorJinStd(25.0)
                .priorLiangStandard(41.25).priorLiangStd(1.5)
                .priorJinToLiangRatio(16.0).ratioConfidence(0.95)
                .source("开皇官制：承北朝大制，统一后以古斗三升为一升").credibility(0.90)
                .build());

        PRIOR_KNOWLEDGE.put(10, WeightSystemPrior.builder()
                .dynastyId(10).dynastyName("唐")
                .priorJinStandard(662.0).priorJinStd(18.0)
                .priorLiangStandard(41.375).priorLiangStd(1.1)
                .priorJinToLiangRatio(16.0).ratioConfidence(0.98)
                .source("开元通宝、唐代度量衡制度完备，承隋制").credibility(0.94)
                .build());

        PRIOR_KNOWLEDGE.put(11, WeightSystemPrior.builder()
                .dynastyId(11).dynastyName("五代十国")
                .priorJinStandard(660.0).priorJinStd(30.0)
                .priorLiangStandard(41.25).priorLiangStd(2.0)
                .priorJinToLiangRatio(16.0).ratioConfidence(0.85)
                .source("战乱时期，大致承唐制但地方差异大").credibility(0.65)
                .build());

        PRIOR_KNOWLEDGE.put(12, WeightSystemPrior.builder()
                .dynastyId(12).dynastyName("北宋")
                .priorJinStandard(638.0).priorJinStd(15.0)
                .priorLiangStandard(39.875).priorLiangStd(0.9)
                .priorJinToLiangRatio(16.0).ratioConfidence(0.97)
                .source("《宋史·律历志》、北宋嘉祐铜则").credibility(0.92)
                .build());

        PRIOR_KNOWLEDGE.put(13, WeightSystemPrior.builder()
                .dynastyId(13).dynastyName("南宋")
                .priorJinStandard(633.0).priorJinStd(18.0)
                .priorLiangStandard(39.5625).priorLiangStd(1.1)
                .priorJinToLiangRatio(16.0).ratioConfidence(0.96)
                .source("南宋铜权、省称制度").credibility(0.88)
                .build());

        PRIOR_KNOWLEDGE.put(14, WeightSystemPrior.builder()
                .dynastyId(14).dynastyName("元")
                .priorJinStandard(633.0).priorJinStd(20.0)
                .priorLiangStandard(39.5625).priorLiangStd(1.2)
                .priorJinToLiangRatio(16.0).ratioConfidence(0.93)
                .source("承宋制，《元典章》记载").credibility(0.82)
                .build());

        PRIOR_KNOWLEDGE.put(15, WeightSystemPrior.builder()
                .dynastyId(15).dynastyName("明")
                .priorJinStandard(585.0).priorJinStd(12.0)
                .priorLiangStandard(36.5625).priorLiangStd(0.75)
                .priorJinToLiangRatio(16.0).ratioConfidence(0.98)
                .source("万历铜权、明代戥秤制度完善").credibility(0.95)
                .build());

        PRIOR_KNOWLEDGE.put(16, WeightSystemPrior.builder()
                .dynastyId(16).dynastyName("清")
                .priorJinStandard(590.0).priorJinStd(10.0)
                .priorLiangStandard(36.875).priorLiangStd(0.6)
                .priorJinToLiangRatio(16.0).ratioConfidence(0.99)
                .source("《清会典》、库平两37.301克（海关两）").credibility(0.96)
                .build());
    }

    public static WeightSystemPrior getPrior(Integer dynastyId) {
        if (dynastyId == null) {
            return null;
        }
        WeightSystemPrior prior = PRIOR_KNOWLEDGE.get(dynastyId);
        if (prior == null) {
            log.warn("朝代ID={} 无先验知识，返回通用先验", dynastyId);
            return getGenericPrior(dynastyId);
        }
        return prior;
    }

    private static WeightSystemPrior getGenericPrior(Integer dynastyId) {
        return WeightSystemPrior.builder()
                .dynastyId(dynastyId)
                .dynastyName("未知")
                .priorJinStandard(450.0).priorJinStd(150.0)
                .priorLiangStandard(28.125).priorLiangStd(10.0)
                .priorJinToLiangRatio(16.0).ratioConfidence(0.60)
                .source("通用弱先验").credibility(0.10)
                .build();
    }

    /**
     * 贝叶斯后验估计
     *
     * θ_posterior = (w_data · θ_data + w_prior · θ_prior) / (w_data + w_prior)
     *
     * w_data = 1/σ_data²,  w_prior = credibility / σ_prior²
     */
    public double getPosteriorJin(double sampleMean, double sampleStd, int sampleCount) {
        if (sampleCount <= 0 || sampleStd <= 0) {
            return priorJinStandard;
        }

        double effectiveSampleStd = sampleStd / Math.sqrt(Math.max(1, sampleCount));

        double wData = 1.0 / (effectiveSampleStd * effectiveSampleStd);
        double wPrior = credibility / (priorJinStd * priorJinStd);

        double posterior = (wData * sampleMean + wPrior * priorJinStandard) / (wData + wPrior);

        log.debug("贝叶斯校正: 样本均值={}g (n={}), 先验={}g, 后验={}g",
                String.format("%.2f", sampleMean), sampleCount,
                String.format("%.2f", priorJinStandard),
                String.format("%.2f", posterior));

        return posterior;
    }

    public double getPosteriorLiang(double sampleMean, double sampleStd, int sampleCount) {
        if (sampleCount <= 0 || sampleStd <= 0) {
            return priorLiangStandard;
        }

        double effectiveSampleStd = sampleStd / Math.sqrt(Math.max(1, sampleCount));

        double wData = 1.0 / (effectiveSampleStd * effectiveSampleStd);
        double wPrior = credibility / (priorLiangStd * priorLiangStd);

        return (wData * sampleMean + wPrior * priorLiangStandard) / (wData + wPrior);
    }

    /**
     * 计算样本聚类中心与先验的马氏距离，用于误判检测
     */
    public double mahalanobisDistanceFromPrior(double sampleJin) {
        return Math.abs(sampleJin - priorJinStandard) / priorJinStd;
    }

    /**
     * 判断聚类结果是否为先验异常（可能是混样、残次权器或其他朝代混入）
     */
    public boolean isPriorOutlier(double sampleJin) {
        double distance = mahalanobisDistanceFromPrior(sampleJin);
        double threshold = 2.0 + (1.0 - credibility) * 2.0;
        return distance > threshold;
    }

    public BigDecimal getPosteriorJin(BigDecimal sampleMean, BigDecimal sampleStd, int sampleCount) {
        double result = getPosteriorJin(
                sampleMean != null ? sampleMean.doubleValue() : priorJinStandard,
                sampleStd != null ? sampleStd.doubleValue() : priorJinStd,
                sampleCount);
        return BigDecimal.valueOf(result).setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal getPosteriorLiang(BigDecimal sampleMean, BigDecimal sampleStd, int sampleCount) {
        double result = getPosteriorLiang(
                sampleMean != null ? sampleMean.doubleValue() : priorLiangStandard,
                sampleStd != null ? sampleStd.doubleValue() : priorLiangStd,
                sampleCount);
        return BigDecimal.valueOf(result).setScale(6, RoundingMode.HALF_UP);
    }
}
