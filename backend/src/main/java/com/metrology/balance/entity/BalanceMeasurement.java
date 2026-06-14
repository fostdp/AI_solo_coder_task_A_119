package com.metrology.balance.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "balance_measurements", indexes = {
    @Index(name = "idx_balance_measurements_balance_id", columnList = "balance_id"),
    @Index(name = "idx_balance_measurements_time", columnList = "measurement_time"),
    @Index(name = "idx_balance_measurements_alert", columnList = "is_alert")
})
public class BalanceMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "balance_id", nullable = false)
    private Long balanceId;

    @Column(name = "measurement_time", nullable = false)
    private LocalDateTime measurementTime;

    @Column(name = "weight_id")
    private Long weightId;

    @Column(name = "nominal_mass", precision = 10, scale = 4)
    private BigDecimal nominalMass;

    @Column(name = "measured_mass", precision = 10, scale = 6)
    private BigDecimal measuredMass;

    @Column(name = "weighing_error", precision = 10, scale = 6)
    private BigDecimal weighingError;

    @Column(name = "relative_error", precision = 12, scale = 8)
    private BigDecimal relativeError;

    @Column(name = "left_arm_length", precision = 10, scale = 4)
    private BigDecimal leftArmLength;

    @Column(name = "right_arm_length", precision = 10, scale = 4)
    private BigDecimal rightArmLength;

    @Column(name = "knife_edge_wear_depth", precision = 10, scale = 6)
    private BigDecimal knifeEdgeWearDepth;

    @Column(name = "knife_edge_friction", precision = 10, scale = 6)
    private BigDecimal knifeEdgeFriction;

    @Column(name = "temperature", precision = 6, scale = 2)
    private BigDecimal temperature;

    @Column(name = "humidity", precision = 6, scale = 2)
    private BigDecimal humidity;

    @Column(name = "is_alert")
    private Boolean isAlert = false;

    @Column(name = "alert_level", length = 20)
    private String alertLevel;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isAlert == null) {
            isAlert = false;
        }
    }
}
