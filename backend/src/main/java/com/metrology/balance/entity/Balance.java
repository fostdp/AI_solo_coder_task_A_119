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
@Table(name = "balances")
public class Balance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "balance_code", nullable = false, length = 50, unique = true)
    private String balanceCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "dynasty_id")
    private Integer dynastyId;

    @Column(name = "balance_type", nullable = false, length = 20)
    private String balanceType;

    @Column(name = "max_capacity", precision = 10, scale = 2)
    private BigDecimal maxCapacity;

    @Column(name = "left_arm_length", precision = 10, scale = 4)
    private BigDecimal leftArmLength;

    @Column(name = "right_arm_length", precision = 10, scale = 4)
    private BigDecimal rightArmLength;

    @Column(name = "knife_edge_radius", precision = 10, scale = 6)
    private BigDecimal knifeEdgeRadius;

    @Column(name = "discovery_location", length = 200)
    private String discoveryLocation;

    @Column(name = "discovery_year")
    private Integer discoveryYear;

    @Column(name = "material", length = 50)
    private String material;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "accuracy_grade", length = 20)
    private String accuracyGrade;

    @Column(name = "allowable_error", precision = 10, scale = 4)
    private BigDecimal allowableError;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
