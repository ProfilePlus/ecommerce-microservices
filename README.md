# 电商订单管理系统 - 微服务项目

## 项目简介

这是一个完整的 Java 微服务项目，基于 Spring Boot 2.7.17 和 Java 8 构建，包含订单服务、库存服务、通知服务和 API 网关。

## 技术栈

### 后端技术栈
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

### 前端技术栈
- **框架**: Vue 3.4+ (Composition API)
- **语言**: TypeScript 5.3+
- **构建工具**: Vite 5.0+
- **UI组件库**: Element Plus 2.5+
- **状态管理**: Pinia 2.1+
- **路由**: Vue Router 4.2+
- **HTTP请求**: Axios 1.6+

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
├── frontend/                  # 前端项目 (端口: 3000)
│   ├── src/
│   │   ├── api/              # API接口封装
│   │   ├── views/            # 页面组件
│   │   ├── router/           # 路由配置
│   │   ├── types/            # TypeScript类型
│   │   ├── App.vue           # 根组件
│   │   └── main.ts           # 应用入口
│   ├── index.html            # HTML模板
│   ├── vite.config.ts        # Vite配置
│   └── package.json          # 前端依赖
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

**后端**：
- JDK 1.8
- Maven 3.6.3+
- Docker（用于运行中间件）

**前端**：
- Node.js >= 16.0.0
- npm >= 7.0.0 或 pnpm >= 7.0.0（推荐）

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

### 1. 启动后端服务

#### 方式一：编译并运行 JAR 包
```bash
# 编译项目
cd ecommerce-microservices
mvn clean package -DskipTests

# 按顺序启动各服务
java -jar order-service/target/order-service-1.0.0.jar
java -jar inventory-service/target/inventory-service-1.0.0.jar
java -jar notification-service/target/notification-service-1.0.0.jar
java -jar gateway-service/target/gateway-service-1.0.0.jar
```

#### 方式二：使用 Maven 运行（开发环境）
```bash
# 在各服务目录下执行
cd order-service
mvn spring-boot:run
```

### 2. 启动前端项目

```bash
# 进入前端目录
cd frontend

# 安装依赖（首次运行）
npm install
# 或使用 pnpm（推荐，更快）
pnpm install

# 启动开发服务器
npm run dev
# 或
pnpm dev
```

前端服务会自动打开浏览器访问 http://localhost:3000

**注意**：前端依赖后端服务，请确保后端所有服务（特别是网关服务）已启动。

## 使用方式

### 方式一：通过前端界面（推荐）

访问前端页面：http://localhost:3000

#### 1. 创建订单
- 点击顶部导航"创建订单"
- 填写订单信息（提供了示例数据）
- 点击"提交订单"
- 自动跳转到订单详情页

#### 2. 查看订单列表
- 在"订单列表"页面
- 输入用户ID（默认1001）
- 点击"查询"按钮

#### 3. 查看订单详情
- 在订单列表中点击"查看详情"
- 查看订单完整信息和处理流程

### 方式二：通过API测试

#### 1. 健康检查
```bash
curl http://localhost:8081/api/orders/health
```

#### 2. 创建订单
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

#### 3. 查询订单
```bash
# 替换 {orderNo} 为实际订单号
curl http://localhost:8000/api/orders/{orderNo}
```

#### 4. 查询用户订单列表
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

## 界面预览

### 前端界面特点
- 🎨 **现代简约设计**：采用Element Plus组件库，界面清爽大方
- 🌈 **渐变色彩**：订单状态卡片采用渐变背景，视觉效果优雅
- 📱 **响应式布局**：适配各种屏幕尺寸
- ⚡ **流畅动画**：页面切换带有淡入淡出效果
- 🔔 **友好提示**：操作反馈及时，错误提示清晰

### 页面功能
1. **创建订单页面**：表单验证、示例数据提示
2. **订单列表页面**：用户订单查询、状态标签展示
3. **订单详情页面**：完整信息展示、流程步骤可视化

## 后续优化方向

### 后端优化
1. 添加分布式事务支持（Seata）
2. 添加限流降级（Sentinel）
3. 完善异常处理和统一响应
4. 添加单元测试和集成测试
5. 容器化部署（Docker + Kubernetes）
6. 持续集成/持续部署（Jenkins）

### 前端优化
1. 添加用户登录和权限管理
2. 完善订单搜索和筛选功能
3. 添加订单状态实时推送（WebSocket）
4. 优化移动端适配
5. 添加数据可视化图表（订单统计）
6. 增加暗黑模式支持

## 代码注释说明

本项目已添加详细的中文注释，包括：

### 1. Java代码注释
- **类级别注释**：每个类都有详细的功能说明、技术亮点、业务流程等
- **方法级别注释**：每个方法都有功能说明、参数说明、返回值说明、异常说明等
- **字段注释**：重要字段都有说明，解释其作用和注意事项
- **行内注释**：关键代码逻辑都有行内注释，便于理解

注释涵盖的内容：
- 启动类：服务功能、技术栈、业务流程
- 实体类：数据模型、字段说明、业务说明
- Repository：数据访问方法、SQL等价说明
- Service：业务逻辑、事务管理、缓存策略、并发控制
- Controller：接口说明、请求示例、响应说明

### 2. 配置文件注释
- **application.yml**：每个配置项都有中文注释
- **bootstrap.yml**：Nacos配置说明
- **pom.xml**：依赖说明、插件说明

### 3. 代码亮点说明
- **缓存策略**：Redis缓存的使用方式和优化策略
- **消息队列**：Kafka和RabbitMQ的使用场景
- **并发控制**：乐观锁防止超卖的实现原理
- **事务管理**：@Transactional的使用和注意事项
- **服务解耦**：通过消息队列实现微服务解耦

### 4. 学习建议
1. 先阅读主POM文件和README，了解整体架构
2. 按照业务流程阅读代码：订单服务 -> 库存服务 -> 通知服务
3. 重点关注Service层的业务逻辑和注释
4. 理解各个中间件的作用和配置
5. 对照注释理解Spring Boot、Spring Cloud的各种注解

## 项目说明

本项目基于《Java微服务全栈技术实战项目部署指南》文档构建，涵盖了微服务开发的核心技术栈，适合作为学习和面试准备的实战项目。

**注释特点**：
- ✅ 所有Java类都有详细的类级别注释
- ✅ 所有public方法都有完整的JavaDoc注释
- ✅ 关键代码逻辑都有行内中文注释
- ✅ 配置文件都有详细的中文说明
- ✅ 技术亮点和最佳实践都有注释说明

---

**作者**: Alex  
**创建时间**: 2025-11-17  
**版本**: 1.0.0  
**注释添加时间**: 2025-11-18  
**前端添加时间**: 2025-11-20  

**前端访问**: http://localhost:3000  
**后端网关**: http://localhost:8000

