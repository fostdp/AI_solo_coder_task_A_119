package com.metrology.balance.event;

import com.metrology.balance.entity.Alert;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AlertTriggeredEvent extends ApplicationEvent {

    private final Alert alert;
    private final String balanceCode;

    public AlertTriggeredEvent(Object source, Alert alert, String balanceCode) {
        super(source);
        this.alert = alert;
        this.balanceCode = balanceCode;
    }
}
