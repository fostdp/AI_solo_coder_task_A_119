package com.metrology.balance.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceSensorData {

    private String balanceCode;
    private LocalDateTime timestamp;
    private BigDecimal nominalMass;
    private BigDecimal measuredMass;
    private BigDecimal weighingError;
    private BigDecimal relativeError;
    private BigDecimal leftArmLength;
    private BigDecimal rightArmLength;
    private BigDecimal knifeEdgeWearDepth;
    private BigDecimal knifeEdgeFriction;
    private BigDecimal temperature;
    private BigDecimal humidity;
}
