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
@Table(name = "weights")
public class Weight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "weight_code", nullable = false, length = 50, unique = true)
    private String weightCode;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "dynasty_id")
    private Integer dynastyId;

    @Column(name = "nominal_mass", precision = 10, scale = 4)
    private BigDecimal nominalMass;

    @Column(name = "actual_mass", precision = 10, scale = 6)
    private BigDecimal actualMass;

    @Column(name = "material", length = 50)
    private String material;

    @Column(name = "discovery_location", length = 200)
    private String discoveryLocation;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
