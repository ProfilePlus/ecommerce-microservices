package com.demo.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 通知业务服务类
 * 
 * 功能说明：
 * 通知服务的核心实现，负责接收消息并发送多渠道通知
 * 
 * 技术亮点：
 * 1. 消息驱动：监听RabbitMQ队列，异步处理通知
 * 2. 多渠道通知：支持短信、邮件、推送等多种通知方式
 * 3. 服务解耦：通过消息队列实现与其他服务的解耦
 * 
 * 业务流程：
 * 库存服务发送RabbitMQ消息 -> 通知服务监听消息 -> 解析消息内容 -> 发送短信/邮件通知
 * 
 * 扩展方向：
 * 1. 集成第三方短信服务（阿里云、腾讯云等）
 * 2. 集成邮件服务（JavaMail、SendGrid等）
 * 3. 集成APP推送服务（极光推送、个推等）
 * 4. 添加消息模板管理
 * 5. 添加通知记录持久化
 * 6. 添加通知发送失败重试机制
 * 
 * @author demo
 * @version 1.0.0
 */
@Slf4j  // Lombok注解：自动生成日志对象log
@Service  // 标识这是一个服务层组件，由Spring容器管理
@RequiredArgsConstructor  // Lombok注解：自动生成包含final字段的构造函数
public class NotificationService {
    
    /**
     * 监听RabbitMQ通知消息（消息消费者）
     * 
     * 功能说明：
     * 监听RabbitMQ的notification.queue队列
     * 当库存服务扣减库存成功后，会发送消息到此队列
     * 通知服务接收到消息后，会发送短信、邮件等通知
     * 
     * 配置说明：
     * - queues: 监听的RabbitMQ队列名称
     * 
     * 消息格式：
     * {
     *   "orderNo": "订单号",
     *   "productId": 商品ID,
     *   "type": "通知类型（INVENTORY_DEDUCTED等）",
     *   "message": "通知消息内容"
     * }
     * 
     * 业务流程：
     * 1. 接收RabbitMQ消息
     * 2. 解析消息内容
     * 3. 记录通知日志
     * 4. 发送短信通知
     * 5. 发送邮件通知
     * 
     * 异常处理：
     * 如果通知发送失败，应该记录失败日志
     * 实际项目中应该有重试机制和告警机制
     * 
     * 性能考虑：
     * - 通知发送是异步的，不阻塞主业务流程
     * - 可以使用线程池并发发送多种通知
     * 
     * @param message 通知消息，包含订单号、通知类型、消息内容等
     */
    @RabbitListener(queues = "notification.queue")
    public void handleNotification(Map<String, Object> message) {
        // 1. 记录接收到的消息
        log.info("=== 收到通知消息 ===");
        log.info("订单号: {}", message.get("orderNo"));
        log.info("类型: {}", message.get("type"));
        log.info("消息: {}", message.get("message"));
        log.info("==================");
        
        // 2. 发送多渠道通知
        // 实际应用中应该根据通知类型和用户偏好选择发送渠道
        sendSms(message);
        sendEmail(message);
        
        // TODO: 扩展更多通知渠道
        // sendAppPush(message);  // APP推送
        // sendWechat(message);   // 微信通知
        // sendDingTalk(message); // 钉钉通知
    }
    
    /**
     * 发送短信通知（私有方法）
     * 
     * 功能说明：
     * 发送短信通知给用户
     * 
     * 当前实现：
     * 仅记录日志，未集成真实的短信服务商
     * 
     * 实际集成步骤：
     * 1. 选择短信服务商（阿里云、腾讯云、华为云等）
     * 2. 注册账号并实名认证
     * 3. 创建短信签名和模板
     * 4. 获取AccessKey和SecretKey
     * 5. 集成对应的SDK
     * 6. 调用SDK发送短信
     * 
     * 示例：
     * 尊敬的用户，您的订单【ORD1700000000000abc12345】库存已扣减，正在为您配货...
     * 
     * @param message 通知消息
     */
    private void sendSms(Map<String, Object> message) {
        log.info("[短信通知] 订单 {} 处理完成", message.get("orderNo"));
        
        // TODO: 集成真实的短信服务商
        // 示例代码：
        // String phone = getUserPhone(message.get("userId"));
        // smsClient.send(phone, "订单处理模板", message);
    }
    
    /**
     * 发送邮件通知（私有方法）
     * 
     * 功能说明：
     * 发送邮件通知给用户
     * 
     * 当前实现：
     * 仅记录日志，未集成真实的邮件服务
     * 
     * 实际集成步骤：
     * 1. 配置SMTP服务器（如：QQ邮箱、163邮箱、企业邮箱）
     * 2. 获取授权码
     * 3. 配置Spring Boot的JavaMail
     * 4. 使用JavaMailSender发送邮件
     * 
     * 邮件内容示例：
     * 主题：订单处理通知
     * 内容：
     * 尊敬的用户，
     * 您的订单【ORD1700000000000abc12345】库存已扣减成功。
     * 我们正在为您配货，请耐心等待。
     * 感谢您的支持！
     * 
     * @param message 通知消息
     */
    private void sendEmail(Map<String, Object> message) {
        log.info("[邮件通知] 订单 {} 处理完成", message.get("orderNo"));
        
        // TODO: 集成真实的邮件服务
        // 示例代码：
        // String email = getUserEmail(message.get("userId"));
        // MimeMessage mimeMessage = mailSender.createMimeMessage();
        // MimeMessageHelper helper = new MimeMessageHelper(mimeMessage);
        // helper.setTo(email);
        // helper.setSubject("订单处理通知");
        // helper.setText(buildEmailContent(message), true);
        // mailSender.send(mimeMessage);
    }
}

