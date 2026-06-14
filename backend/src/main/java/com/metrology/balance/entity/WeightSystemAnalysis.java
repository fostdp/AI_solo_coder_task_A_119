package com.metrology.balance.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "weight_system_analyses")
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class WeightSystemAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dynasty_id")
    private Integer dynastyId;

    @Column(name = "analysis_time", nullable = false)
    private LocalDateTime analysisTime;

    @Column(name = "sample_count")
    private Integer sampleCount;

    @Column(name = "jin_standard", precision = 10, scale = 4)
    private BigDecimal jinStandard;

    @Column(name = "liang_standard", precision = 10, scale = 6)
    private BigDecimal liangStandard;

    @Column(name = "cluster_count")
    private Integer clusterCount;

    @Column(name = "silhouette_score", precision = 8, scale = 6)
    private BigDecimal silhouetteScore;

    @Type(type = "jsonb")
    @Column(name = "clusters", columnDefinition = "jsonb")
    private Map<String, Object> clusters;

    @Column(name = "method", length = 50)
    private String method = "K_MEANS";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (analysisTime == null) {
            analysisTime = LocalDateTime.now();
        }
        if (method == null) {
            method = "K_MEANS";
        }
    }
}
