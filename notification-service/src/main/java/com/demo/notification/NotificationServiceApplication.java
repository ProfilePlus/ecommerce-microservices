package com.demo.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 通知服务启动类
 * 
 * 功能说明：
 * 1. 消息消费：监听RabbitMQ的通知队列消息
 * 2. 多渠道通知：支持短信、邮件、推送等多种通知方式
 * 3. 异步处理：通过消息队列实现异步通知，不阻塞主业务流程
 * 
 * 业务流程：
 * 1. 监听RabbitMQ的通知队列
 * 2. 接收到库存扣减成功的消息
 * 3. 发送短信通知用户
 * 4. 发送邮件通知用户
 * 5. 记录通知日志
 * 
 * 技术栈：
 * - Spring Boot: 基础框架
 * - RabbitMQ: 消息队列（消费者）
 * - Nacos: 服务注册与发现
 * 
 * 扩展方向：
 * - 集成短信服务商API（阿里云、腾讯云等）
 * - 集成邮件服务（JavaMail、SendGrid等）
 * - 集成APP推送服务（极光推送、个推等）
 * - 消息模板管理
 * - 通知记录持久化
 * 
 * @author demo
 * @version 1.0.0
 */
@SpringBootApplication  // Spring Boot应用标识注解，启用自动配置
@EnableDiscoveryClient  // 启用服务发现客户端，将通知服务注册到Nacos
public class NotificationServiceApplication {
    
    /**
     * 应用程序主入口
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}

