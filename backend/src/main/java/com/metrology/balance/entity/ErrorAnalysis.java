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
@Table(name = "error_analyses", indexes = {
    @Index(name = "idx_error_analyses_balance_id", columnList = "balance_id")
})
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class ErrorAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "balance_id", nullable = false)
    private Long balanceId;

    @Column(name = "analysis_time", nullable = false)
    private LocalDateTime analysisTime;

    @Column(name = "simulation_count", nullable = false)
    private Integer simulationCount;

    @Column(name = "mean_error", precision = 12, scale = 8)
    private BigDecimal meanError;

    @Column(name = "std_deviation", precision = 12, scale = 8)
    private BigDecimal stdDeviation;

    @Column(name = "combined_uncertainty", precision = 12, scale = 8)
    private BigDecimal combinedUncertainty;

    @Column(name = "expanded_uncertainty", precision = 12, scale = 8)
    private BigDecimal expandedUncertainty;

    @Column(name = "coverage_factor", precision = 4, scale = 2)
    private BigDecimal coverageFactor = new BigDecimal("2.0");

    @Column(name = "friction_contribution", precision = 8, scale = 4)
    private BigDecimal frictionContribution;

    @Column(name = "arm_length_contribution", precision = 8, scale = 4)
    private BigDecimal armLengthContribution;

    @Column(name = "weight_contribution", precision = 8, scale = 4)
    private BigDecimal weightContribution;

    @Column(name = "accuracy_grade", length = 20)
    private String accuracyGrade;

    @Type(type = "jsonb")
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private Map<String, Object> rawData;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (analysisTime == null) {
            analysisTime = LocalDateTime.now();
        }
        if (coverageFactor == null) {
            coverageFactor = new BigDecimal("2.0");
        }
    }
}
