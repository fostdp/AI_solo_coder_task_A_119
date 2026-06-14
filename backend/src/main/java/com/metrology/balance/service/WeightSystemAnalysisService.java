package com.metrology.balance.service;

import com.metrology.balance.dto.ClusterAnalysisResult;
import com.metrology.balance.entity.Weight;
import com.metrology.balance.entity.WeightSystemAnalysis;
import com.metrology.balance.model.WeightSystemPrior;
import com.metrology.balance.repository.WeightRepository;
import com.metrology.balance.repository.WeightSystemAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeightSystemAnalysisService {

    private final WeightRepository weightRepository;
    private final WeightSystemAnalysisRepository analysisRepository;

    private static final int DEFAULT_MAX_CLUSTERS = 10;
    private static final int DEFAULT_K_MEANS_ITERATIONS = 100;
    private static final double JIN_TO_LIANG_RATIO = 16.0;

    @Transactional
    public ClusterAnalysisResult analyzeWeightSystem(Integer dynastyId, int clusterCount) {
        List<Weight> weights;
        if (dynastyId != null) {
            weights = weightRepository.findByDynastyIdOrderByNominalMass(dynastyId);
        } else {
            weights = weightRepository.findAll();
        }

        if (weights.isEmpty()) {
            throw new IllegalStateException("没有足够的砝码数据进行分析");
        }

        List<Double> rawActualMasses = weights.stream()
                .filter(w -> w.getActualMass() != null)
                .map(w -> w.getActualMass().doubleValue())
                .collect(Collectors.toList());

        if (rawActualMasses.size() < 3) {
            throw new IllegalStateException("有效砝码样本不足，无法进行聚类分析");
        }

        WeightSystemPrior prior = null;
        List<Double> actualMasses = rawActualMasses;

        if (dynastyId != null) {
            prior = WeightSystemPrior.getPrior(dynastyId);
            if (prior != null) {
                log.info("应用朝代[{}]先验知识: 斤={}g±{}g, 两={}g±{}g, 可信度={}",
                        prior.getDynastyName(),
                        prior.getPriorJinStandard(), prior.getPriorJinStd(),
                        prior.getPriorLiangStandard(), prior.getPriorLiangStd(),
                        prior.getCredibility());

                actualMasses = filterByPrior(rawActualMasses, prior);

                if (actualMasses.size() < 3) {
                    log.warn("先验过滤后样本不足({}→{})，回退到原始样本",
                            rawActualMasses.size(), actualMasses.size());
                    actualMasses = rawActualMasses;
                } else {
                    log.info("先验过滤: 样本从{}减少到{}, 剔除{}个可疑样本",
                            rawActualMasses.size(), actualMasses.size(),
                            rawActualMasses.size() - actualMasses.size());
                }
            }
        }

        int k = clusterCount > 0 ? clusterCount : determineOptimalClusters(actualMasses);

        ClusterAnalysisResult result = performKMeansClustering(actualMasses, k, weights, dynastyId, prior);

        saveAnalysisResult(dynastyId, result, weights.size());

        return result;
    }

    private List<Double> filterByPrior(List<Double> masses, WeightSystemPrior prior) {
        double priorJin = prior.getPriorJinStandard();
        double priorLiang = prior.getPriorLiangStandard();
        double tolerance = 3.0 * prior.getPriorJinStd();

        return masses.stream().filter(m -> {
            double ratioToJin = m / priorJin;
            double ratioToLiang = m / priorLiang;

            boolean nearLiangMultiple = false;
            for (int n = 1; n <= 32; n++) {
                double expected = n * priorLiang;
                if (Math.abs(m - expected) < 3.0 * n * prior.getPriorLiangStd()) {
                    nearLiangMultiple = true;
                    break;
                }
            }

            boolean nearJinMultiple = false;
            for (int n = 1; n <= 4; n++) {
                double expected = n * priorJin;
                if (Math.abs(m - expected) < tolerance) {
                    nearJinMultiple = true;
                    break;
                }
            }

            boolean isOutlier = prior.isPriorOutlier(m) && !nearLiangMultiple && !nearJinMultiple;

            if (isOutlier) {
                log.debug("剔除先验异常样本: {}g, 距先验{}σ",
                        String.format("%.3f", m),
                        String.format("%.2f", prior.mahalanobisDistanceFromPrior(m)));
            }

            return !isOutlier;
        }).collect(Collectors.toList());
    }

    private int determineOptimalClusters(List<Double> data) {
        int maxClusters = Math.min(DEFAULT_MAX_CLUSTERS, data.size() / 3);
        if (maxClusters < 2) return 2;
        if (maxClusters > 8) maxClusters = 8;

        double bestSilhouette = -1;
        int bestK = 2;

        for (int k = 2; k <= maxClusters; k++) {
            try {
                double silhouette = calculateSilhouetteScore(data, k);
                if (silhouette > bestSilhouette) {
                    bestSilhouette = silhouette;
                    bestK = k;
                }
            } catch (Exception e) {
                log.warn("计算k={}时轮廓系数失败: {}", k, e.getMessage());
            }
        }

        return bestK;
    }

    private ClusterAnalysisResult performKMeansClustering(
            List<Double> data, int k, List<Weight> weights, Integer dynastyId, WeightSystemPrior prior) {

        List<DoublePoint> points = new ArrayList<>();
        for (Double value : data) {
            points.add(new DoublePoint(new double[]{value}));
        }

        KMeansPlusPlusClusterer<DoublePoint> clusterer =
                new KMeansPlusPlusClusterer<>(k, DEFAULT_K_MEANS_ITERATIONS);

        List<CentroidCluster<DoublePoint>> clusters = clusterer.cluster(points);

        List<ClusterAnalysisResult.ClusterInfo> clusterInfos = new ArrayList<>();

        for (int i = 0; i < clusters.size(); i++) {
            CentroidCluster<DoublePoint> cluster = clusters.get(i);
            double center = cluster.getCenter().getPoint()[0];

            List<Double> clusterData = cluster.getPoints().stream()
                    .map(p -> p.getPoint()[0])
                    .collect(Collectors.toList());

            double min = clusterData.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double max = clusterData.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double stdDev = calculateStdDev(clusterData, center);

            ClusterAnalysisResult.ClusterInfo info = ClusterAnalysisResult.ClusterInfo.builder()
                    .clusterId(i)
                    .center(BigDecimal.valueOf(center).setScale(6, RoundingMode.HALF_UP))
                    .sampleCount(clusterData.size())
                    .minValue(BigDecimal.valueOf(min).setScale(6, RoundingMode.HALF_UP))
                    .maxValue(BigDecimal.valueOf(max).setScale(6, RoundingMode.HALF_UP))
                    .stdDev(BigDecimal.valueOf(stdDev).setScale(6, RoundingMode.HALF_UP))
                    .build();

            clusterInfos.add(info);
        }

        clusterInfos.sort(Comparator.comparing(ClusterAnalysisResult.ClusterInfo::getCenter));

        double silhouetteScore = calculateSilhouetteScore(data, k);

        ClusterAnalysisResult.ClusterInfo liangCluster = findBestLiangCluster(clusterInfos, prior);

        BigDecimal rawLiangStandard = liangCluster.getCenter();
        BigDecimal rawLiangStd = liangCluster.getStdDev();
        int liangSampleCount = liangCluster.getSampleCount();

        BigDecimal liangStandard;
        BigDecimal jinStandard;
        String method;

        if (prior != null) {
            BigDecimal posteriorLiang = prior.getPosteriorLiang(
                    rawLiangStandard, rawLiangStd, liangSampleCount);
            BigDecimal posteriorJin = prior.getPosteriorJin(
                    rawLiangStandard.multiply(BigDecimal.valueOf(JIN_TO_LIANG_RATIO)),
                    rawLiangStd.multiply(BigDecimal.valueOf(JIN_TO_LIANG_RATIO)),
                    liangSampleCount);

            double rawLiang = rawLiangStandard.doubleValue();
            double adjustedLiang = posteriorLiang.doubleValue();
            double correctionPercent = Math.abs(adjustedLiang - rawLiang) / rawLiang * 100;

            log.info("Bayes校正: 原始两标准={}g, 先验={}g(±{}g), 后验={}g, 校正幅度={}%, " +
                            "先验可信度={}, 样本数={}",
                    String.format("%.4f", rawLiang),
                    String.format("%.4f", prior.getPriorLiangStandard()),
                    String.format("%.4f", prior.getPriorLiangStd()),
                    String.format("%.4f", adjustedLiang),
                    String.format("%.2f", correctionPercent),
                    prior.getCredibility(),
                    liangSampleCount);

            liangStandard = posteriorLiang;
            jinStandard = posteriorJin;
            method = "K_MEANS_BAYES_PRIOR";
        } else {
            liangStandard = rawLiangStandard;
            jinStandard = rawLiangStandard.multiply(BigDecimal.valueOf(JIN_TO_LIANG_RATIO))
                    .setScale(4, RoundingMode.HALF_UP);
            method = "K_MEANS";
        }

        return ClusterAnalysisResult.builder()
                .clusterCount(k)
                .silhouetteScore(BigDecimal.valueOf(silhouetteScore).setScale(6, RoundingMode.HALF_UP))
                .method(method)
                .jinStandard(jinStandard)
                .liangStandard(liangStandard)
                .clusters(clusterInfos)
                .build();
    }

    private ClusterAnalysisResult.ClusterInfo findBestLiangCluster(
            List<ClusterAnalysisResult.ClusterInfo> clusters, WeightSystemPrior prior) {

        if (clusters == null || clusters.isEmpty()) {
            return ClusterAnalysisResult.ClusterInfo.builder()
                    .center(BigDecimal.valueOf(15.625))
                    .stdDev(BigDecimal.valueOf(1.0))
                    .sampleCount(1)
                    .build();
        }

        if (prior != null) {
            double priorLiang = prior.getPriorLiangStandard();
            double priorJin = prior.getPriorJinStandard();

            ClusterAnalysisResult.ClusterInfo bestMatch = null;
            double bestScore = Double.MAX_VALUE;

            for (ClusterAnalysisResult.ClusterInfo cluster : clusters) {
                double center = cluster.getCenter().doubleValue();

                double liangScore = Math.abs(center - priorLiang) / prior.getPriorLiangStd();
                double minJinScore = Double.MAX_VALUE;
                for (int n = 1; n <= 16; n++) {
                    double expectedJin = n * priorJin;
                    double score = Math.abs(center - expectedJin) / (n * prior.getPriorJinStd());
                    if (score < minJinScore) minJinScore = score;
                }

                double minLiangMultipleScore = Double.MAX_VALUE;
                for (int n = 1; n <= 32; n++) {
                    double expected = n * priorLiang;
                    double score = Math.abs(center - expected) / (n * prior.getPriorLiangStd());
                    if (score < minLiangMultipleScore) minLiangMultipleScore = score;
                }

                double combinedScore = Math.min(liangScore, Math.min(minJinScore, minLiangMultipleScore));
                double penalty = cluster.getSampleCount() > 0
                        ? 1.0 / Math.sqrt(cluster.getSampleCount()) : 10.0;
                combinedScore += penalty * 0.5;

                if (combinedScore < bestScore) {
                    bestScore = combinedScore;
                    bestMatch = cluster;
                }
            }

            if (bestMatch != null && bestScore < 4.0) {
                log.info("先验匹配成功: 最近聚类中心={}g, 匹配得分={}",
                        String.format("%.4f", bestMatch.getCenter().doubleValue()),
                        String.format("%.3f", bestScore));
                return bestMatch;
            } else {
                log.warn("所有聚类与先验偏差较大(最佳得分={}), 将使用最小聚类中心",
                        String.format("%.3f", bestScore));
            }
        }

        return clusters.get(0);
    }

    private double calculateSilhouetteScore(List<Double> data, int k) {
        if (data.size() < 3 || k < 2 || k >= data.size()) {
            return -1;
        }

        List<DoublePoint> points = new ArrayList<>();
        for (Double value : data) {
            points.add(new DoublePoint(new double[]{value}));
        }

        KMeansPlusPlusClusterer<DoublePoint> clusterer =
                new KMeansPlusPlusClusterer<>(k, DEFAULT_K_MEANS_ITERATIONS);

        List<CentroidCluster<DoublePoint>> clusters = clusterer.cluster(points);

        double totalSilhouette = 0;
        int count = 0;

        for (int i = 0; i < clusters.size(); i++) {
            List<DoublePoint> clusterPoints = clusters.get(i).getPoints();

            for (DoublePoint point : clusterPoints) {
                double a = calculateAverageDistance(point, clusterPoints);

                double b = Double.MAX_VALUE;
                for (int j = 0; j < clusters.size(); j++) {
                    if (i != j) {
                        double dist = calculateAverageDistance(point, clusters.get(j).getPoints());
                        if (dist < b) b = dist;
                    }
                }

                double maxAB = Math.max(a, b);
                if (maxAB > 0) {
                    totalSilhouette += (b - a) / maxAB;
                    count++;
                }
            }
        }

        return count > 0 ? totalSilhouette / count : -1;
    }

    private double calculateAverageDistance(DoublePoint point, List<DoublePoint> points) {
        if (points.size() <= 1) return 0;

        double total = 0;
        double p = point.getPoint()[0];

        for (DoublePoint other : points) {
            total += Math.abs(p - other.getPoint()[0]);
        }

        return total / (points.size() - 1);
    }

    private double calculateStdDev(List<Double> data, double mean) {
        if (data.size() < 2) return 0;

        double sum = 0;
        for (double d : data) {
            sum += (d - mean) * (d - mean);
        }

        return Math.sqrt(sum / (data.size() - 1));
    }

    private BigDecimal estimateLiangStandard(List<ClusterAnalysisResult.ClusterInfo> clusters) {
        if (clusters.isEmpty()) return BigDecimal.ZERO;

        ClusterAnalysisResult.ClusterInfo smallestCluster = clusters.get(0);
        if (smallestCluster.getSampleCount() >= 2) {
            return smallestCluster.getCenter();
        }

        double weightedSum = 0;
        int totalCount = 0;

        for (ClusterAnalysisResult.ClusterInfo cluster : clusters) {
            weightedSum += cluster.getCenter().doubleValue() * cluster.getSampleCount();
            totalCount += cluster.getSampleCount();
        }

        if (totalCount == 0) return BigDecimal.ZERO;

        return BigDecimal.valueOf(weightedSum / totalCount)
                .setScale(6, RoundingMode.HALF_UP);
    }

    private void saveAnalysisResult(Integer dynastyId, ClusterAnalysisResult result, int sampleCount) {
        WeightSystemAnalysis analysis = new WeightSystemAnalysis();
        analysis.setDynastyId(dynastyId);
        analysis.setSampleCount(sampleCount);
        analysis.setJinStandard(result.getJinStandard());
        analysis.setLiangStandard(result.getLiangStandard());
        analysis.setClusterCount(result.getClusterCount());
        analysis.setSilhouetteScore(result.getSilhouetteScore());
        analysis.setMethod(result.getMethod());

        Map<String, Object> clustersMap = new HashMap<>();
        List<Map<String, Object>> clusterList = new ArrayList<>();
        for (ClusterAnalysisResult.ClusterInfo info : result.getClusters()) {
            Map<String, Object> clusterMap = new HashMap<>();
            clusterMap.put("clusterId", info.getClusterId());
            clusterMap.put("center", info.getCenter());
            clusterMap.put("sampleCount", info.getSampleCount());
            clusterMap.put("minValue", info.getMinValue());
            clusterMap.put("maxValue", info.getMaxValue());
            clusterMap.put("stdDev", info.getStdDev());
            clusterList.add(clusterMap);
        }
        clustersMap.put("items", clusterList);
        analysis.setClusters(clustersMap);

        analysisRepository.save(analysis);
    }

    public List<WeightSystemAnalysis> getAnalysisHistory(Integer dynastyId) {
        if (dynastyId != null) {
            return analysisRepository.findByDynastyIdOrderByAnalysisTimeDesc(dynastyId);
        }
        return analysisRepository.findAllByOrderByAnalysisTimeDesc();
    }

    public WeightSystemAnalysis getLatestAnalysis(Integer dynastyId) {
        if (dynastyId != null) {
            return analysisRepository.findLatestByDynastyId(dynastyId).orElse(null);
        }
        return analysisRepository.findAllByOrderByAnalysisTimeDesc()
                .stream().findFirst().orElse(null);
    }
}
