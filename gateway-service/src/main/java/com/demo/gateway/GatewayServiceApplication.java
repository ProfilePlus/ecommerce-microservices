package com.demo.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关服务启动类
 * 
 * 功能说明：
 * 1. 作为系统的统一入口，所有外部请求都通过网关进行路由
 * 2. 提供路由转发功能，将请求转发到对应的微服务
 * 3. 集成Nacos服务发现，实现动态路由和负载均衡
 * 4. 配置跨域支持，允许前端跨域访问
 * 
 * 技术栈：
 * - Spring Cloud Gateway: API网关框架
 * - Nacos: 服务注册与发现中心
 * 
 * @author demo
 * @version 1.0.0
 */
@SpringBootApplication  // Spring Boot应用标识注解，启用自动配置
@EnableDiscoveryClient  // 启用服务发现客户端，将网关注册到Nacos
public class GatewayServiceApplication {
    
    /**
     * 应用程序主入口
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}

