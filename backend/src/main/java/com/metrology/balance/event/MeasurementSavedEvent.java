package com.metrology.balance.event;

import com.metrology.balance.entity.BalanceMeasurement;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class MeasurementSavedEvent extends ApplicationEvent {

    private final BalanceMeasurement measurement;
    private final String balanceCode;
    private final boolean isAlert;
    private final String alertLevel;

    public MeasurementSavedEvent(Object source,
                                 BalanceMeasurement measurement,
                                 String balanceCode,
                                 boolean isAlert,
                                 String alertLevel) {
        super(source);
        this.measurement = measurement;
        this.balanceCode = balanceCode;
        this.isAlert = isAlert;
        this.alertLevel = alertLevel;
    }
}
