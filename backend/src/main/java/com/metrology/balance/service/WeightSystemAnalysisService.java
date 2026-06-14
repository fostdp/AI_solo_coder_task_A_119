package com.metrology.balance.service;

import com.metrology.balance.dto.ClusterAnalysisResult;
import com.metrology.balance.entity.Weight;
import com.metrology.balance.entity.WeightSystemAnalysis;
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

        List<Double> actualMasses = weights.stream()
                .filter(w -> w.getActualMass() != null)
                .map(w -> w.getActualMass().doubleValue())
                .collect(Collectors.toList());

        if (actualMasses.size() < 3) {
            throw new IllegalStateException("有效砝码样本不足，无法进行聚类分析");
        }

        int k = clusterCount > 0 ? clusterCount : determineOptimalClusters(actualMasses);

        ClusterAnalysisResult result = performKMeansClustering(actualMasses, k, weights, dynastyId);

        saveAnalysisResult(dynastyId, result, weights.size());

        return result;
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
            List<Double> data, int k, List<Weight> weights, Integer dynastyId) {

        List<DoublePoint> points = new ArrayList<>();
        for (Double value : data) {
            points.add(new DoublePoint(new double[]{value}));
        }

        KMeansPlusPlusClusterer<DoublePoint> clusterer =
                new KMeansPlusPlusClusterer<>(k, DEFAULT_K_MEANS_ITERATIONS);

        List<CentroidCluster<DoublePoint>> clusters = clusterer.cluster(points);

        List<ClusterAnalysisResult.ClusterInfo> clusterInfos = new ArrayList<>();
        List<Double> clusterCenters = new ArrayList<>();

        for (int i = 0; i < clusters.size(); i++) {
            CentroidCluster<DoublePoint> cluster = clusters.get(i);
            double center = cluster.getCenter().getPoint()[0];
            clusterCenters.add(center);

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

        BigDecimal liangStandard = estimateLiangStandard(clusterInfos);
        BigDecimal jinStandard = liangStandard.multiply(BigDecimal.valueOf(JIN_TO_LIANG_RATIO))
                .setScale(4, RoundingMode.HALF_UP);

        return ClusterAnalysisResult.builder()
                .clusterCount(k)
                .silhouetteScore(BigDecimal.valueOf(silhouetteScore).setScale(6, RoundingMode.HALF_UP))
                .method("K_MEANS")
                .jinStandard(jinStandard)
                .liangStandard(liangStandard)
                .clusters(clusterInfos)
                .build();
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
