package com.demo.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 订单服务启动类
 * 
 * 功能说明：
 * 1. 订单管理：创建订单、查询订单、查询用户订单列表
 * 2. 消息发布：订单创建后通过Kafka发送消息给库存服务
 * 3. 缓存管理：使用Redis缓存订单数据，提高查询性能
 * 4. 数据持久化：使用PostgreSQL存储订单数据
 * 
 * 业务流程：
 * 1. 接收用户订单请求
 * 2. 生成唯一订单号
 * 3. 保存订单到数据库
 * 4. 缓存订单到Redis
 * 5. 发送订单创建消息到Kafka
 * 
 * 技术栈：
 * - Spring Boot: 基础框架
 * - Spring Data JPA: 数据持久化
 * - PostgreSQL: 关系型数据库
 * - Redis: 缓存
 * - Kafka: 消息队列（生产者）
 * - Nacos: 服务注册与发现
 * 
 * @author demo
 * @version 1.0.0
 */
@SpringBootApplication  // Spring Boot应用标识注解，启用自动配置
@EnableDiscoveryClient  // 启用服务发现客户端，将订单服务注册到Nacos
public class OrderServiceApplication {
    
    /**
     * 应用程序主入口
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}

