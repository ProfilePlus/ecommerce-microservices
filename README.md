# 电商订单管理系统 - 微服务项目

## 项目简介

这是一个完整的 Java 微服务项目，基于 Spring Boot 2.7.17 和 Java 8 构建，包含订单服务、库存服务、通知服务和 API 网关。

## 技术栈

- **Java**: 1.8
- **Maven**: 3.6.3+
- **Spring Boot**: 2.7.17
- **Spring Cloud**: 2021.0.8
- **Spring Cloud Alibaba**: 2021.0.5.0
- **数据库**: PostgreSQL 14
- **缓存**: Redis 7.0
- **消息队列**: Kafka + RabbitMQ
- **服务注册**: Nacos 2.2
- **监控**: Prometheus + Grafana
- **链路追踪**: SkyWalking

## 项目结构

```
ecommerce-microservices/
├── common/                     # 公共模块
├── order-service/              # 订单服务 (端口: 8081)
│   ├── src/main/java/com/demo/order/
│   │   ├── OrderServiceApplication.java
│   │   ├── controller/         # 控制器层
│   │   ├── service/           # 业务逻辑层
│   │   ├── entity/            # 实体类
│   │   ├── dto/               # 数据传输对象
│   │   └── repository/        # 数据访问层
│   └── src/main/resources/
│       ├── bootstrap.yml      # Nacos 配置
│       └── application.yml    # 应用配置
├── inventory-service/         # 库存服务 (端口: 8082)
├── notification-service/      # 通知服务 (端口: 8083)
├── gateway-service/           # API 网关 (端口: 8000)
└── pom.xml                    # 父 POM 文件
```

## 核心功能

### 1. 订单服务 (order-service)
- 创建订单
- 查询订单
- 查询用户订单列表
- 订单信息缓存到 Redis
- 发送订单创建消息到 Kafka

### 2. 库存服务 (inventory-service)
- 监听 Kafka 订单消息
- 扣减商品库存（乐观锁）
- 发送库存扣减成功消息到 RabbitMQ
- 库存信息缓存到 Redis

### 3. 通知服务 (notification-service)
- 监听 RabbitMQ 通知消息
- 发送短信/邮件通知（模拟）

### 4. API 网关 (gateway-service)
- 统一入口
- 路由转发
- 负载均衡

## 使用前准备

### 1. 环境要求
- JDK 1.8
- Maven 3.6.3+
- Docker（用于运行中间件）

### 2. 启动中间件（请参考文档中的 Docker 部署章节）
需要启动以下服务：
- PostgreSQL (端口: 5432)
- Redis (端口: 6379)
- Kafka (端口: 9092)
- RabbitMQ (端口: 5672, 15672)
- Nacos (端口: 8848)

### 3. 配置文件修改
在每个服务的 `bootstrap.yml` 和 `application.yml` 中，将 `192.168.xxx.xxx` 替换为实际的服务器 IP 地址。

例如：
```yaml
# bootstrap.yml
spring:
  cloud:
    nacos:
      config:
        server-addr: 192.168.1.100:8848  # 替换为实际 IP
      discovery:
        server-addr: 192.168.1.100:8848  # 替换为实际 IP
```

### 4. 初始化数据库
按照文档中的 PostgreSQL 初始化脚本，创建表并插入测试数据。

## 编译和运行

### 1. 编译项目
```bash
cd ecommerce-microservices
mvn clean package -DskipTests
```

### 2. 启动服务（按顺序启动）

#### 方式一：直接运行 JAR 包
```bash
# 1. 启动订单服务
java -jar order-service/target/order-service-1.0.0.jar

# 2. 启动库存服务
java -jar inventory-service/target/inventory-service-1.0.0.jar

# 3. 启动通知服务
java -jar notification-service/target/notification-service-1.0.0.jar

# 4. 启动网关服务
java -jar gateway-service/target/gateway-service-1.0.0.jar
```

#### 方式二：使用 Maven 运行（开发环境）
```bash
# 在各服务目录下执行
cd order-service
mvn spring-boot:run
```

## API 测试

### 1. 健康检查
```bash
curl http://localhost:8081/api/orders/health
```

### 2. 创建订单
```bash
curl -X POST http://localhost:8000/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1001,
    "productId": 1001,
    "productName": "iPhone 15 Pro",
    "quantity": 2,
    "totalAmount": 16998.00
  }'
```

### 3. 查询订单
```bash
# 替换 {orderNo} 为实际订单号
curl http://localhost:8000/api/orders/{orderNo}
```

### 4. 查询用户订单列表
```bash
curl http://localhost:8000/api/orders/user/1001
```

## 监控与管理

### 1. Nacos 控制台
- URL: http://localhost:8848/nacos
- 用户名/密码: nacos/nacos
- 功能: 查看服务注册情况

### 2. RabbitMQ 管理界面
- URL: http://localhost:15672
- 用户名/密码: admin/Admin@123
- 功能: 查看队列和消息

### 3. Prometheus 监控
- URL: http://localhost:9091
- 功能: 查看指标数据

### 4. Grafana 可视化
- URL: http://localhost:3000
- 用户名/密码: admin/Admin@123
- 功能: 可视化监控面板

## 业务流程

```
用户下单 
  ↓
订单服务创建订单
  ↓
保存到 PostgreSQL
  ↓
缓存到 Redis (30分钟)
  ↓
发送 Kafka 消息 (order-created)
  ↓
库存服务消费消息
  ↓
扣减库存 (乐观锁)
  ↓
发送 RabbitMQ 消息 (通知)
  ↓
通知服务消费消息
  ↓
发送短信/邮件通知
```

## 常见问题

### 1. 启动时连接不上 Nacos
检查 Nacos 是否启动，以及配置文件中的 IP 地址是否正确。

### 2. Redis 连接失败
检查 Redis 是否启动，密码是否配置正确（默认密码: redis）。

### 3. Kafka 消息发送失败
检查 Kafka 和 Zookeeper 是否启动，Topic 是否已创建。

### 4. 数据库连接失败
检查 PostgreSQL 是否启动，用户名密码是否正确（admin/Admin@123）。

## 后续优化方向

1. 添加分布式事务支持（Seata）
2. 添加限流降级（Sentinel）
3. 完善异常处理和统一响应
4. 添加单元测试和集成测试
5. 容器化部署（Docker + Kubernetes）
6. 持续集成/持续部署（Jenkins）

## 项目说明

本项目基于《Java微服务全栈技术实战项目部署指南》文档构建，涵盖了微服务开发的核心技术栈，适合作为学习和面试准备的实战项目。

---

**作者**: Demo  
**创建时间**: 2025-11-17  
**版本**: 1.0.0

