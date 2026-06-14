package com.metrology.balance.modules.alarm_ws;

import com.metrology.balance.entity.Alert;
import com.metrology.balance.event.AlertTriggeredEvent;
import com.metrology.balance.event.MeasurementSavedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    @Value("${websocket.topic:/topic/alerts}")
    private String alertTopic;

    @Async
    @EventListener
    public void onAlertTriggered(AlertTriggeredEvent event) {
        Alert alert = event.getAlert();
        try {
            messagingTemplate.convertAndSend(alertTopic, alert);
            log.info("告警已通过WebSocket推送: balance={}, alertId={}, level={}",
                    event.getBalanceCode(), alert.getId(), alert.getAlertLevel());
        } catch (Exception e) {
            log.warn("推送告警到WebSocket失败: {}", e.getMessage());
        }
    }

    @EventListener
    public void onMeasurementSaved(MeasurementSavedEvent event) {
        if (event.isAlert()) {
            log.debug("收到测量保存事件，已告警: balance={}, level={}",
                    event.getBalanceCode(), event.getAlertLevel());
        }
    }
}
