package com.metrology.balance.event;

import com.metrology.balance.dto.MonteCarloResult;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ErrorAnalysisCompletedEvent extends ApplicationEvent {

    private final Long balanceId;
    private final MonteCarloResult result;

    public ErrorAnalysisCompletedEvent(Object source, Long balanceId, MonteCarloResult result) {
        super(source);
        this.balanceId = balanceId;
        this.result = result;
    }
}
