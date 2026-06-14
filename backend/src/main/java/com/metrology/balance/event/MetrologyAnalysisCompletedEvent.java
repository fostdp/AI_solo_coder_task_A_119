package com.metrology.balance.event;

import com.metrology.balance.dto.ClusterAnalysisResult;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class MetrologyAnalysisCompletedEvent extends ApplicationEvent {

    private final Integer dynastyId;
    private final ClusterAnalysisResult result;
    private final int sampleCount;

    public MetrologyAnalysisCompletedEvent(Object source,
                                           Integer dynastyId,
                                           ClusterAnalysisResult result,
                                           int sampleCount) {
        super(source);
        this.dynastyId = dynastyId;
        this.result = result;
        this.sampleCount = sampleCount;
    }
}
