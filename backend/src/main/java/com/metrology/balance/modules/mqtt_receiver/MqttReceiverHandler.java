package com.metrology.balance.modules.mqtt_receiver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttReceiverHandler {

    private final MqttReceiverService receiverService;

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<String> message) {
        try {
            String payload = message.getPayload();
            log.debug("收到MQTT消息: {}", payload);
            receiverService.processRawPayload(payload);
            log.debug("MQTT消息处理完成");
        } catch (Exception e) {
            log.error("处理MQTT消息失败: {}", e.getMessage(), e);
        }
    }
}
