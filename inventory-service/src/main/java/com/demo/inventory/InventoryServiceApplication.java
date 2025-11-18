package com.demo.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 库存服务启动类
 * 
 * 功能说明：
 * 1. 库存管理：查询库存、扣减库存
 * 2. 消息消费：监听Kafka订单创建消息，自动扣减库存
 * 3. 消息发布：库存扣减成功后通过RabbitMQ发送通知消息
 * 4. 缓存管理：使用Redis缓存库存数据，提高查询性能
 * 5. 乐观锁：使用版本号实现库存扣减的并发控制
 * 
 * 业务流程：
 * 1. 监听Kafka的订单创建消息
 * 2. 根据商品ID查询库存
 * 3. 检查库存是否充足
 * 4. 扣减库存并更新版本号（乐观锁）
 * 5. 更新Redis缓存
 * 6. 发送库存扣减成功消息到RabbitMQ
 * 
 * 技术栈：
 * - Spring Boot: 基础框架
 * - Spring Data JPA: 数据持久化
 * - PostgreSQL: 关系型数据库
 * - Redis: 缓存
 * - Kafka: 消息队列（消费者）
 * - RabbitMQ: 消息队列（生产者）
 * - Nacos: 服务注册与发现
 * 
 * @author demo
 * @version 1.0.0
 */
@SpringBootApplication  // Spring Boot应用标识注解，启用自动配置
@EnableDiscoveryClient  // 启用服务发现客户端，将库存服务注册到Nacos
public class InventoryServiceApplication {
    
    /**
     * 应用程序主入口
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}

