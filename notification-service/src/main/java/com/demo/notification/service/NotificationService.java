package com.demo.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    
    /**
     * 监听RabbitMQ通知消息
     */
    @RabbitListener(queues = "notification.queue")
    public void handleNotification(Map<String, Object> message) {
        log.info("=== 收到通知消息 ===");
        log.info("订单号: {}", message.get("orderNo"));
        log.info("类型: {}", message.get("type"));
        log.info("消息: {}", message.get("message"));
        log.info("==================");
        
        // 实际应用中可以发送短信、邮件、推送等
        sendSms(message);
        sendEmail(message);
    }
    
    private void sendSms(Map<String, Object> message) {
        log.info("[短信通知] 订单 {} 处理完成", message.get("orderNo"));
    }
    
    private void sendEmail(Map<String, Object> message) {
        log.info("[邮件通知] 订单 {} 处理完成", message.get("orderNo"));
    }
}

