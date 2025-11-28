# Spring Cloud Gateway 网关原理与实现效果详解

## 📚 目录
- [一、网关是什么](#一网关是什么)
- [二、Spring Cloud Gateway 核心原理](#二spring-cloud-gateway-核心原理)
- [三、本项目网关架构](#三本项目网关架构)
- [四、网关工作流程详解](#四网关工作流程详解)
- [五、实现效果演示](#五实现效果演示)
- [六、核心功能解析](#六核心功能解析)
- [七、实际应用场景](#七实际应用场景)

---

## 一、网关是什么

### 1.1 概念定义

API网关是微服务架构中的**统一入口**，类似于小区的门卫或者酒店的前台：

```
┌─────────────┐
│   前端应用   │
│  (Vue3/H5)  │
└──────┬──────┘
       │
       │ 所有请求都通过这一个入口
       ▼
┌─────────────────────────────────┐
│      API Gateway (网关)          │
│         :8000                    │  ◄──── 统一入口
│  - 路由转发                      │
│  - 服务发现                      │
│  - 负载均衡                      │
│  - 跨域处理                      │
│  - 统一鉴权                      │
└────────┬───────┬────────┬───────┘
         │       │        │
    ┌────▼──┐ ┌──▼───┐ ┌─▼──────┐
    │订单服务│ │库存服务│ │通知服务│
    │ :8001 │ │ :8002 │ │ :8003 │
    └───────┘ └──────┘ └────────┘
```

### 1.2 为什么需要网关？

**没有网关的情况（传统架构）：**
```javascript
// 前端需要记住所有服务的地址
const orderAPI = 'http://192.168.1.10:8001/orders'
const inventoryAPI = 'http://192.168.1.10:8002/inventory'
const notificationAPI = 'http://192.168.1.10:8003/notifications'

// 问题：
// 1. 服务地址变更，前端需要同步修改
// 2. 每个服务都需要配置跨域
// 3. 每个服务都需要实现鉴权逻辑
// 4. 无法统一管理限流、日志、监控
```

**使用网关后：**
```javascript
// 前端只需要知道网关地址
const baseURL = 'http://192.168.1.10:8000/api'

// 所有请求都通过网关
axios.get(`${baseURL}/orders/123`)        // 自动路由到订单服务
axios.get(`${baseURL}/inventory/456`)     // 自动路由到库存服务
axios.post(`${baseURL}/orders`, data)     // 自动路由到订单服务

// 优势：
// ✅ 前端不关心后端服务如何部署
// ✅ 统一配置跨域、鉴权、限流
// ✅ 后端服务可以随意扩容缩容
// ✅ 统一的安全防护和监控
```

### 1.3 问题根源深度剖析

#### 问题1：服务地址硬编码导致的紧耦合

**问题根源：**

```javascript
// ❌ 传统方式：前端直接硬编码后端服务地址
const orderAPI = 'http://192.168.1.10:8001/orders'
const inventoryAPI = 'http://192.168.1.10:8002/inventory'
const userAPI = 'http://192.168.1.10:8003/users'

// 发起请求
axios.get(orderAPI + '/123')
```

**为什么这样设计有问题？**

1. **违反依赖倒置原则（DIP）**
   - 前端（高层模块）直接依赖后端服务的具体地址（底层细节）
   - 任何底层变化都会影响高层模块

2. **部署灵活性丧失**
```
场景：订单服务从8001端口迁移到9001端口
├─► 后端配置修改：server.port=9001 ✅
├─► 前端代码修改：orderAPI = 'http://...9001/orders' ❌
├─► 重新构建前端 ❌
├─► 重新发布前端 ❌
└─► 用户清除浏览器缓存 ❌

影响：
- 运维成本高：一个端口变更需要修改多个系统
- 风险大：前端忘记修改导致请求失败
- 发布复杂：前后端必须协调发布
```

3. **扩容缩容困难**
```
场景：订单服务需要扩容到3个实例
当前：1个实例 (192.168.1.10:8001)
目标：3个实例 (8001, 8002, 8003)

传统方案的困境：
├─► 方案A：前端仍然请求8001，单实例压力未减轻 ❌
├─► 方案B：前端改代码轮询请求三个地址 ❌
│   └─► 需要前端实现负载均衡逻辑
│   └─► 前端代码复杂度大增
│   └─► 无法感知实例健康状态
└─► 方案C：前面加Nginx ✅ (但这就是网关的雏形了)
```

4. **跨域名访问受限**
```
开发环境：
  前端：http://localhost:5173
  后端：http://localhost:8001 ✅ 可以访问

生产环境：
  前端：https://www.example.com
  后端：https://api-internal.company.com ❌ 跨域问题
  
问题：
- 前端直接访问内部域名，DNS无法解析
- 即使能解析，也存在安全风险（暴露内部网络）
```

**网关如何解决？**

```javascript
// ✅ 使用网关：前端只需要知道网关地址
const baseURL = 'http://192.168.1.10:8000/api'

// 所有请求都通过网关
axios.get(`${baseURL}/orders/123`)
```

**解决原理：**

```
┌──────────────────────────────────────────────────────────────┐
│              引入中间层（网关）解耦                            │
└──────────────────────────────────────────────────────────────┘

前端代码：
  只知道网关地址：http://gateway:8000
  只知道业务路径：/api/orders/123
  
网关配置：
  routes:
    - id: order-service
      uri: lb://order-service  ← 服务名称（逻辑地址）
      predicates:
        - Path=/api/orders/**

服务注册（Nacos）：
  order-service:
    - 192.168.1.10:8001 ← 实际物理地址
    - 192.168.1.10:8002
    - 192.168.1.10:8003

优势：
1. 前端与后端物理地址完全解耦
2. 后端服务地址变更，前端无感知
3. 后端扩容缩容，前端无需修改
4. 统一域名，无跨域问题
```

---

#### 问题2：CORS跨域配置分散且重复

**问题根源：浏览器同源策略（Same-Origin Policy）**

**什么是同源策略？**

浏览器的安全机制，限制一个源的文档/脚本如何与另一个源的资源交互。

```javascript
// 同源判断规则
协议 + 域名 + 端口 完全相同才是"同源"

示例：
源地址：http://localhost:5173

✅ 同源：
  http://localhost:5173/page1
  http://localhost:5173/api/users

❌ 跨域：
  http://localhost:8001/orders      ← 端口不同
  https://localhost:5173/orders     ← 协议不同
  http://127.0.0.1:5173/orders      ← 域名不同（即使是同一个IP）
```

**为什么需要同源策略？**

防止恶意网站窃取数据：

```html
<!-- 恶意网站 http://evil.com -->
<script>
  // 如果没有同源策略，恶意网站可以：
  
  // 1. 读取用户在其他网站的Cookie
  fetch('http://bank.com/api/account')
    .then(res => res.json())
    .then(data => {
      // 窃取用户银行账户信息
      sendToHacker(data)
    })
  
  // 2. 发起伪造请求
  fetch('http://bank.com/api/transfer', {
    method: 'POST',
    body: JSON.stringify({
      to: 'hacker_account',
      amount: 10000
    })
  })
</script>
```

**传统方式的问题：每个服务都要配置CORS**

```java
// ❌ Order Service 需要配置
@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                    .allowedOrigins("http://localhost:5173", "https://www.example.com")
                    .allowedMethods("GET", "POST", "PUT", "DELETE")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
            }
        };
    }
}

// ❌ Inventory Service 也要配置（重复代码）
@Configuration
public class CorsConfig {
    // 完全相同的代码...
}

// ❌ Notification Service 也要配置（重复代码）
@Configuration
public class CorsConfig {
    // 完全相同的代码...
}

// ❌ User Service 也要配置（重复代码）
// ❌ Product Service 也要配置（重复代码）
// ... 每个服务都要写一遍
```

**分散配置导致的问题：**

1. **配置不一致**
```java
// Order Service允许的域名
.allowedOrigins("http://localhost:5173", "https://www.example.com")

// Inventory Service允许的域名（少配置了一个）
.allowedOrigins("http://localhost:5173")

// 结果：生产环境前端访问库存服务时跨域报错 ❌
```

2. **维护成本高**
```
场景：新增一个前端域名 https://mobile.example.com

需要修改：
├─► Order Service 配置 ❌
├─► Inventory Service 配置 ❌
├─► Notification Service 配置 ❌
├─► User Service 配置 ❌
├─► Product Service 配置 ❌
└─► ... 10个服务，需要改10次

遗漏风险：
- 开发人员忘记改某个服务
- 配置格式不统一导致错误
- 发布需要协调所有服务
```

3. **预检请求（Preflight Request）增加延迟**

```
浏览器发送复杂请求时的流程：

1. 浏览器先发送OPTIONS预检请求
   OPTIONS http://localhost:8001/api/orders
   Origin: http://localhost:5173
   Access-Control-Request-Method: POST
   Access-Control-Request-Headers: Content-Type

2. 订单服务响应预检
   Access-Control-Allow-Origin: http://localhost:5173
   Access-Control-Allow-Methods: POST
   Access-Control-Max-Age: 3600

3. 浏览器再发送真正的POST请求
   POST http://localhost:8001/api/orders
   
问题：
- 每个服务都要处理预检请求
- 增加一次网络往返（RTT）
- 如果服务多，多次跨域请求会有多次预检
```

**网关如何解决？**

```yaml
# ✅ 网关统一配置CORS（只需配置一次）
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':  # 对所有路径生效
            allowed-origins: 
              - "http://localhost:5173"
              - "https://www.example.com"
              - "https://mobile.example.com"
            allowed-methods: 
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowed-headers: "*"
            allow-credentials: true
            max-age: 3600
```

**解决原理：**

```
┌──────────────────────────────────────────────────────────────┐
│              网关统一处理CORS                                  │
└──────────────────────────────────────────────────────────────┘

1. 前端发起请求
   ├─► 浏览器：我要请求 http://gateway:8000/api/orders
   ├─► 浏览器：我的源是 http://localhost:5173
   └─► 浏览器：先发送OPTIONS预检

2. 网关处理预检（Pre-Filter）
   ├─► 接收OPTIONS请求
   ├─► 检查全局CORS配置
   ├─► 添加响应头：
   │   ├─► Access-Control-Allow-Origin: http://localhost:5173
   │   ├─► Access-Control-Allow-Methods: GET, POST, ...
   │   ├─► Access-Control-Allow-Headers: *
   │   └─► Access-Control-Max-Age: 3600
   └─► 直接返回200，无需转发到后端服务 ✅ 减少一次转发

3. 浏览器发送真正的请求
   ├─► POST http://gateway:8000/api/orders
   └─► 网关转发到后端服务（后端服务无需处理CORS）

4. 后端服务返回响应
   └─► 网关添加CORS响应头（Post-Filter）
       └─► Access-Control-Allow-Origin: http://localhost:5173

5. 浏览器接收响应
   └─► 检查CORS响应头 ✅ 通过

优势：
1. 后端服务无需配置CORS，专注业务逻辑
2. 配置统一，修改一处即可
3. 预检请求在网关层终止，减少后端压力
4. 前端看到的只有一个域名，天然同源
```

**更优雅的方案：前后端同域**

```nginx
# Nginx配置
server {
    listen 80;
    server_name www.example.com;
    
    # 前端静态资源
    location / {
        root /var/www/frontend;
        index index.html;
    }
    
    # API请求代理到网关
    location /api/ {
        proxy_pass http://gateway:8000/api/;
    }
}

访问路径：
前端页面：https://www.example.com/
API请求： https://www.example.com/api/orders
         ↓
协议：https = https ✅
域名：www.example.com = www.example.com ✅
端口：443 = 443 ✅

结果：完全同源，无需CORS配置！
```

---

#### 问题3：鉴权逻辑分散导致安全隐患

**问题根源：认证授权的本质**

**认证（Authentication）vs 授权（Authorization）**

```
认证：你是谁？（验证身份）
  ├─► 用户登录：用户名 + 密码
  ├─► 颁发令牌：JWT Token
  └─► 后续请求携带Token

授权：你能做什么？（验证权限）
  ├─► 检查用户角色：admin / user / guest
  ├─► 检查资源权限：是否有权限访问该订单
  └─► 返回结果或拒绝访问
```

**传统方式的问题：每个服务都要实现鉴权**

```java
// ❌ Order Service 的鉴权逻辑
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Long id, 
                          @RequestHeader("Authorization") String token) {
        // 1. 验证Token
        if (token == null || !token.startsWith("Bearer ")) {
            throw new UnauthorizedException("缺少认证令牌");
        }
        
        try {
            Claims claims = jwtUtil.parseToken(token.substring(7));
            Long userId = claims.get("userId", Long.class);
            
            // 2. 检查权限
            Order order = orderService.getById(id);
            if (!order.getUserId().equals(userId)) {
                throw new ForbiddenException("无权访问此订单");
            }
            
            return order;
        } catch (JwtException e) {
            throw new UnauthorizedException("令牌无效或已过期");
        }
    }
}

// ❌ Inventory Service 也要写（重复代码）
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    
    @Autowired
    private JwtUtil jwtUtil;  // 重复依赖
    
    @GetMapping("/check")
    public Stock checkStock(@RequestHeader("Authorization") String token,
                           @RequestParam Long productId) {
        // 又是一遍相同的验证逻辑...
        if (token == null || !token.startsWith("Bearer ")) {
            throw new UnauthorizedException("缺少认证令牌");
        }
        // ... 重复代码
    }
}

// ❌ User Service、Product Service... 每个都要写
```

**分散鉴权的严重问题：**

1. **代码重复，维护成本高**
```java
// 统计：10个微服务
├─► 每个服务都有JwtUtil工具类（10份重复代码）
├─► 每个Controller方法都要验证Token（100+个方法）
└─► Token验证逻辑分散在100+个地方

修改场景：JWT密钥变更
├─► 需要修改10个服务的JwtUtil配置 ❌
├─► 需要协调发布10个服务 ❌
└─► 遗漏一个服务会导致认证失败 ❌
```

2. **安全策略不一致**
```java
// Order Service 的Token过期时间：24小时
jwtUtil.setExpirationTime(24 * 60 * 60 * 1000);

// Inventory Service 的Token过期时间：1小时（开发者忘记统一）
jwtUtil.setExpirationTime(60 * 60 * 1000);

// User Service 的Token签名算法：HS256
jwtUtil.setAlgorithm(SignatureAlgorithm.HS256);

// Product Service 的Token签名算法：HS512（配置不一致）
jwtUtil.setAlgorithm(SignatureAlgorithm.HS512);

问题：
- 用户体验不一致（有的接口1小时过期，有的24小时）
- 安全强度不一致（算法强度不同）
- 难以审计和监控
```

3. **性能问题：重复验证**
```
场景：创建订单的完整流程

1. 前端请求网关
   POST /api/orders
   Header: Authorization: Bearer <token>

2. Order Service验证Token  ← 第1次验证
   ├─► 解析JWT
   ├─► 验证签名（CPU密集）
   └─► 检查过期时间

3. Order Service调用Inventory Service
   POST /api/inventory/deduct
   Header: Authorization: Bearer <token>  ← 传递Token

4. Inventory Service再次验证Token  ← 第2次验证
   ├─► 解析JWT（重复操作）
   ├─► 验证签名（重复操作）
   └─► 检查过期时间

5. Inventory Service调用Notification Service
   POST /api/notifications/send
   Header: Authorization: Bearer <token>

6. Notification Service第三次验证Token  ← 第3次验证
   └─► 重复相同的验证逻辑

问题：
- 同一个Token被验证3次，浪费CPU
- JWT验证涉及密码学运算，性能开销大
- 每次验证约1-5ms，累积延迟明显
```

4. **无法实现统一安全策略**
```java
// 需求：禁用某个Token（用户退出登录或账号被封）

传统方案困境：
├─► JWT是无状态的，一旦颁发就无法撤销
├─► 除非引入黑名单机制
└─► 但黑名单需要在每个服务中实现 ❌

需求：限制单个用户并发请求数（防止API滥用）

传统方案困境：
├─► 需要在每个服务中维护用户请求计数
├─► 分散的计数无法真正限制用户
└─► 用户可以绕过某个服务的限制 ❌
```

**网关如何解决？**

```java
// ✅ 网关统一鉴权（GlobalFilter）
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        
        // 白名单：登录、注册等接口无需鉴权
        if (isWhitelist(path)) {
            return chain.filter(exchange);
        }
        
        // 获取Token
        String token = request.getHeaders().getFirst("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return unauthorized(exchange, "缺少认证令牌");
        }
        
        token = token.substring(7);
        
        // 检查Token黑名单（支持注销功能）
        if (redisTemplate.hasKey("blacklist:" + token)) {
            return unauthorized(exchange, "令牌已失效");
        }
        
        // 验证Token
        try {
            Claims claims = jwtUtil.parseToken(token);
            Long userId = claims.get("userId", Long.class);
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);
            
            // 检查Token是否即将过期，自动续期
            long expirationTime = claims.getExpiration().getTime();
            long now = System.currentTimeMillis();
            if (expirationTime - now < 10 * 60 * 1000) {  // 少于10分钟
                String newToken = jwtUtil.refreshToken(claims);
                // 在响应头中返回新Token
                exchange.getResponse().getHeaders().add("X-New-Token", newToken);
            }
            
            // 将用户信息添加到请求头，传递给后端服务
            ServerHttpRequest newRequest = request.mutate()
                .header("X-User-Id", userId.toString())
                .header("X-Username", username)
                .header("X-User-Role", role)
                .build();
            
            // 后端服务无需再验证Token，直接使用用户信息
            return chain.filter(exchange.mutate().request(newRequest).build());
            
        } catch (ExpiredJwtException e) {
            return unauthorized(exchange, "令牌已过期");
        } catch (JwtException e) {
            return unauthorized(exchange, "令牌无效");
        }
    }
    
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        String body = String.format("{\"code\": 401, \"message\": \"%s\"}", message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes());
        return response.writeWith(Mono.just(buffer));
    }
    
    @Override
    public int getOrder() {
        return -100;  // 优先级最高，第一个执行
    }
}
```

**解决原理：**

```
┌──────────────────────────────────────────────────────────────┐
│              网关统一鉴权（单点认证）                          │
└──────────────────────────────────────────────────────────────┘

流程：

1. 用户登录
   ├─► POST /api/auth/login {username, password}
   ├─► Auth Service验证用户名密码
   └─► 返回JWT Token

2. 前端请求API
   ├─► POST /api/orders
   ├─► Header: Authorization: Bearer <token>
   └─► 请求到达网关

3. 网关鉴权（AuthenticationFilter）
   ├─► 提取Token
   ├─► 验证Token签名 ✅
   ├─► 检查过期时间 ✅
   ├─► 检查黑名单 ✅
   ├─► 解析用户信息：userId=123, role=admin
   ├─► 添加自定义请求头：
   │   ├─► X-User-Id: 123
   │   ├─► X-Username: zhangsan
   │   └─► X-User-Role: admin
   └─► 转发请求到后端服务

4. 后端服务（Order Service）
   ├─► 接收请求，直接读取请求头
   ├─► Long userId = request.getHeader("X-User-Id");
   ├─► String role = request.getHeader("X-User-Role");
   └─► 无需验证Token，直接使用用户信息 ✅

5. 服务间调用（Order → Inventory）
   ├─► Order Service调用Inventory Service
   ├─► 传递用户信息：headers.set("X-User-Id", userId)
   └─► Inventory Service直接使用，无需再验证 ✅

优势：
1. Token只验证一次（网关），后端服务零开销 ✅
2. 鉴权逻辑集中管理，易于维护 ✅
3. 安全策略统一，配置一致 ✅
4. 支持Token黑名单、自动续期等高级功能 ✅
5. 后端服务专注业务逻辑，无需关心认证 ✅
```

**安全增强：防止内部服务被绕过**

```yaml
# 问题：如果有人直接访问后端服务（绕过网关），怎么办？
# 直接请求：http://order-service:8001/api/orders

# 解决方案1：网络隔离
# Order Service部署在内网，外网无法直接访问
# 只有网关在DMZ区，可以访问内外网

# 解决方案2：IP白名单
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://order-service
          filters:
            - AddRequestHeader=X-Gateway-Flag, internal-gateway-secret

# Order Service验证请求来源
@Component
public class GatewayCheckInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, 
                             HttpServletResponse response, 
                             Object handler) {
        String flag = request.getHeader("X-Gateway-Flag");
        if (!"internal-gateway-secret".equals(flag)) {
            response.setStatus(403);
            response.getWriter().write("Direct access forbidden");
            return false;
        }
        return true;
    }
}

# 结果：只有通过网关的请求才会携带特殊标记
# 直接访问后端服务会被拒绝 ✅
```

---

#### 问题4：限流、日志、监控分散导致运维困难

**问题根源：横切关注点（Cross-Cutting Concerns）**

**什么是横切关注点？**

在软件系统中，有些功能需要在多个模块中重复出现：
- 日志记录：每个接口都要记录
- 性能监控：每个接口都要统计耗时
- 限流控制：每个接口都要防止滥用
- 异常处理：每个接口都要捕获异常

这些功能"横切"整个系统，传统方式处理它们会导致代码重复。

**传统方式的问题：AOP分散在各个服务**

```java
// ❌ Order Service 的日志切面
@Aspect
@Component
public class LoggingAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);
    
    @Around("execution(* com.demo.order.controller.*.*(..))")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        String method = joinPoint.getSignature().getName();
        
        logger.info("Order Service - Method: {} started", method);
        
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;
            logger.info("Order Service - Method: {} completed in {}ms", method, duration);
            return result;
        } catch (Exception e) {
            logger.error("Order Service - Method: {} failed: {}", method, e.getMessage());
            throw e;
        }
    }
}

// ❌ Inventory Service 也要写（重复代码）
@Aspect
@Component
public class LoggingAspect {
    // 完全相同的代码...
}

// ❌ Notification Service 也要写
// ❌ User Service 也要写
// ... 每个服务都要写一遍
```

**分散监控的问题：**

1. **无法全局限流**
```java
// Order Service 限流：每个用户每秒最多10次请求
@RateLimiter(limit = 10, window = 1)
@GetMapping("/api/orders")
public List<Order> getOrders() { ... }

// Inventory Service 限流：每个用户每秒最多10次请求
@RateLimiter(limit = 10, window = 1)
@GetMapping("/api/inventory")
public Stock getStock() { ... }

// 问题：恶意用户可以绕过限流
用户A发起：
├─► Order Service：10次/秒 ✅ 未超限
├─► Inventory Service：10次/秒 ✅ 未超限
├─► Notification Service：10次/秒 ✅ 未超限
└─► 总计：30次/秒 ❌ 实际已经超限

真实意图：限制每个用户对整个系统的请求频率
实际效果：只限制了单个服务，用户可以攻击多个服务
```

2. **日志分散，难以追踪**
```
场景：用户投诉订单创建失败，需要排查问题

问题：一个完整的业务流程涉及多个服务
├─► Gateway：接收请求
├─► Order Service：创建订单
├─► Inventory Service：扣减库存
└─► Notification Service：发送通知

传统方式的困境：
├─► 需要分别查看4个服务的日志
├─► 日志格式不统一，难以关联
├─► 无法确定具体哪个服务出错
└─► 排查一个问题需要30分钟 ❌

理想方式：
├─► 通过Request ID关联所有日志
├─► 在统一平台查看完整调用链
└─► 快速定位问题，5分钟搞定 ✅
```

3. **监控指标分散，缺乏全局视角**
```
Order Service监控：
├─► QPS: 1000
├─► 平均响应时间: 50ms
└─► 错误率: 0.1%

Inventory Service监控：
├─► QPS: 800
├─► 平均响应时间: 30ms
└─► 错误率: 5%  ← 高错误率！

问题：
- Order Service看起来正常
- 但Inventory Service错误率高，会导致订单失败
- 分散监控无法发现服务间的依赖问题
- 需要统一视角才能发现瓶颈
```

**网关如何解决？**

```java
// ✅ 网关统一限流
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 提取用户ID（从之前的鉴权Filter中获取）
        String userId = request.getHeaders().getFirst("X-User-Id");
        if (userId == null) {
            userId = request.getRemoteAddress().getAddress().getHostAddress();  // 使用IP
        }
        
        // 限流Key：user:{userId}:requests
        String key = "rate_limit:user:" + userId;
        
        // 使用Redis实现滑动窗口限流
        Long count = redisTemplate.opsForValue().increment(key, 1);
        if (count == 1) {
            // 第一次请求，设置过期时间1秒
            redisTemplate.expire(key, 1, TimeUnit.SECONDS);
        }
        
        // 限制：每个用户每秒最多100次请求（全局限制）
        if (count > 100) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            
            String body = "{\"code\": 429, \"message\": \"请求过于频繁，请稍后再试\"}";
            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes());
            return response.writeWith(Mono.just(buffer));
        }
        
        // 添加限流信息到响应头
        exchange.getResponse().getHeaders().add("X-RateLimit-Limit", "100");
        exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(100 - count));
        
        return chain.filter(exchange);
    }
    
    @Override
    public int getOrder() {
        return -50;  // 在鉴权之后执行
    }
}

// ✅ 网关统一日志
@Component
public class LoggingFilter implements GlobalFilter, Ordered {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = UUID.randomUUID().toString();
        
        // 记录请求信息
        logger.info("[{}] Request: {} {} from {}", 
            requestId,
            request.getMethod(),
            request.getPath(),
            request.getRemoteAddress());
        
        // 添加Request ID到请求头，传递给后端服务
        ServerHttpRequest newRequest = request.mutate()
            .header("X-Request-Id", requestId)
            .build();
        
        long startTime = System.currentTimeMillis();
        
        return chain.filter(exchange.mutate().request(newRequest).build())
            .then(Mono.fromRunnable(() -> {
                long duration = System.currentTimeMillis() - startTime;
                ServerHttpResponse response = exchange.getResponse();
                
                // 记录响应信息
                logger.info("[{}] Response: {} in {}ms", 
                    requestId,
                    response.getStatusCode(),
                    duration);
            }));
    }
    
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;  // 最高优先级，第一个执行
    }
}
```

**解决原理：**

```
┌──────────────────────────────────────────────────────────────┐
│              网关统一监控（单点观测）                          │
└──────────────────────────────────────────────────────────────┘

1. 请求到达网关
   └─► http://gateway:8000/api/orders

2. 生成Request ID
   └─► X-Request-Id: 550e8400-e29b-41d4-a716-446655440000

3. 限流检查（全局）
   ├─► 查询Redis：user:123的请求次数
   ├─► 当前：85次/秒 ✅ 未超限
   └─► 继续执行

4. 记录请求日志（网关层）
   └─► [550e8400] Request: POST /api/orders from 192.168.1.100

5. 转发到Order Service
   ├─► 携带X-Request-Id请求头
   └─► Order Service记录日志：
       └─► [550e8400] OrderService: Creating order...

6. Order Service调用Inventory Service
   ├─► 携带X-Request-Id请求头
   └─► Inventory Service记录日志：
       └─► [550e8400] InventoryService: Deducting stock...

7. 响应返回网关
   └─► 记录响应日志：
       └─► [550e8400] Response: 200 in 120ms

8. 统一日志平台（ELK）
   └─► 搜索Request ID：550e8400
       └─► 查看完整调用链：
           ├─► Gateway: Request received
           ├─► OrderService: Order created
           ├─► InventoryService: Stock deducted
           └─► Gateway: Response sent (120ms)

优势：
1. 全局限流，防止滥用 ✅
2. 统一Request ID，追踪完整链路 ✅
3. 集中日志，快速定位问题 ✅
4. 统一监控指标，全局视角 ✅
```

**监控大屏示例：**

```
┌──────────────────────────────────────────────────────────────┐
│              实时监控大屏（Grafana）                           │
└──────────────────────────────────────────────────────────────┘

┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  总QPS: 5000    │  │  平均延迟: 80ms │  │  错误率: 0.2%  │
└─────────────────┘  └─────────────────┘  └─────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    各服务QPS分布                              │
│  Order Service:     ████████████ 2000 QPS                   │
│  Inventory Service: ████████ 1500 QPS                       │
│  User Service:      ██████ 1000 QPS                         │
│  Notification:      ████ 500 QPS                            │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    服务响应时间                              │
│  Gateway → Order:        50ms                               │
│  Order → Inventory:      30ms                               │
│  Order → Notification:   20ms                               │
│  Total:                  100ms                              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    限流统计                                  │
│  今日限流次数：      1,234 次                                │
│  被限流的用户：      23 人                                   │
│  Top限流IP：         192.168.1.100 (456次)                  │
└─────────────────────────────────────────────────────────────┘

数据来源：全部来自网关
优势：统一采集、统一展示、统一告警
```

---

## 二、Spring Cloud Gateway 核心原理

### 2.1 三大核心概念

#### 1️⃣ Route（路由）
路由是网关的基本构建单元，包含：
- **ID**：路由唯一标识
- **URI**：目标服务地址
- **Predicates**：断言（匹配条件）
- **Filters**：过滤器（处理逻辑）

```yaml
# 示例：订单服务路由
- id: order-service                    # 路由ID
  uri: lb://order-service              # 目标URI（lb表示负载均衡）
  predicates:
    - Path=/api/orders/**              # 匹配条件：路径以/api/orders/开头
  filters:
    - StripPrefix=1                    # 去掉前缀（可选）
```

#### 2️⃣ Predicate（断言/路由匹配器）
断言用于判断请求是否符合路由条件：

```yaml
predicates:
  - Path=/api/orders/**           # 路径匹配
  - Method=GET,POST               # HTTP方法匹配
  - Header=X-Request-Id, \d+      # 请求头匹配
  - Query=token                   # 查询参数匹配
  - After=2023-01-01T00:00:00Z    # 时间匹配
```

**本项目使用：**
- 订单服务：`Path=/api/orders/**`
- 库存服务：`Path=/api/inventory/**`
- 通知服务：`Path=/api/notifications/**`

#### 3️⃣ Filter（过滤器）
过滤器在请求到达目标服务前/后进行处理：

```
Pre-Filter (前置)        Route         Post-Filter (后置)
     │                     │                  │
     ▼                     ▼                  ▼
  鉴权检查  ──────►  路由转发  ──────►  统一响应格式
  限流控制              │              记录日志
  添加请求头            │              修改响应头
```

### 2.2 工作流程

```
┌──────────────────────────────────────────────────────────────┐
│                     Gateway 工作流程                           │
└──────────────────────────────────────────────────────────────┘

1️⃣ 请求到达
   └─► http://localhost:8000/api/orders/123

2️⃣ Gateway Handler Mapping（路由匹配）
   ├─► 检查路由规则
   ├─► 匹配到：id=order-service, predicate=Path=/api/orders/**
   └─► 目标：lb://order-service

3️⃣ Gateway Web Handler（执行过滤器链）
   ├─► Pre-Filter：跨域处理、鉴权检查、限流控制
   ├─► Route：通过Nacos查询order-service实例列表
   │   └─► [192.168.1.10:8001, 192.168.1.10:8002]
   ├─► Load Balancer：选择一个实例（轮询/随机）
   │   └─► 选中：192.168.1.10:8001
   └─► 转发请求：http://192.168.1.10:8001/api/orders/123

4️⃣ 目标服务处理
   └─► Order Service 处理业务逻辑并返回结果

5️⃣ Post-Filter（后置处理）
   ├─► 记录响应日志
   ├─► 添加响应头
   └─► 统一响应格式

6️⃣ 返回给客户端
   └─► 响应数据返回给前端
```

### 2.3 核心组件

```java
// 1. Gateway Handler Mapping
// 作用：接收请求，匹配路由
RoutePredicateHandlerMapping
  └─► 遍历所有路由定义
  └─► 找到匹配的路由
  └─► 返回目标URI

// 2. Gateway Web Handler
// 作用：执行过滤器链
FilteringWebHandler
  ├─► GlobalFilter（全局过滤器）
  │   ├─► LoadBalancerClientFilter（负载均衡）
  │   ├─► ForwardRoutingFilter（转发）
  │   └─► NettyRoutingFilter（HTTP客户端）
  └─► GatewayFilter（路由级过滤器）
      ├─► AddRequestHeaderGatewayFilter
      └─► StripPrefixGatewayFilter

// 3. Nacos Discovery Client
// 作用：从Nacos获取服务实例列表
NacosDiscoveryClient
  └─► 查询服务：order-service
  └─► 返回实例：[instance1:8001, instance2:8002]

// 4. Load Balancer
// 作用：从多个实例中选择一个
ReactiveLoadBalancer
  ├─► 策略：RoundRobin（轮询，默认）
  ├─► 策略：Random（随机）
  └─► 返回：选中的实例
```

---

## 三、本项目网关架构

### 3.1 技术栈

```xml
<dependencies>
    <!-- 1. Spring Cloud Gateway：网关核心 -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>
    
    <!-- 2. Nacos Discovery：服务发现 -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>
    
    <!-- 3. Nacos Config：配置中心 -->
    <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
    </dependency>
    
    <!-- 4. Actuator：健康检查与监控 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

### 3.2 配置详解

#### bootstrap.yml（优先加载）

```yaml
spring:
  application:
    name: gateway-service  # 服务名称
    
  cloud:
    nacos:
      # Nacos配置中心
      config:
        server-addr: 192.168.1.10:8848
        file-extension: yaml
        namespace: public
        # 💡 实现配置热更新：修改Nacos配置，网关自动生效
        
      # Nacos服务发现
      discovery:
        server-addr: 192.168.1.10:8848
        # 💡 将网关注册到Nacos，实现服务间调用
```

#### application.yml（应用配置）

```yaml
server:
  port: 8000  # 网关端口（统一入口）

spring:
  application:
    name: gateway-service
  
  cloud:
    nacos:
      discovery:
        server-addr: 192.168.1.10:8848
    
    gateway:
      routes:
        # ========== 订单服务路由 ==========
        - id: order-service
          uri: lb://order-service  # lb://表示从Nacos获取服务实例，自动负载均衡
          predicates:
            - Path=/api/orders/**  # 匹配所有/api/orders/开头的请求
          # 示例：
          # 请求：http://localhost:8000/api/orders/123
          # 转发：http://order-service实例/api/orders/123
        
        # ========== 库存服务路由 ==========
        - id: inventory-service
          uri: lb://inventory-service
          predicates:
            - Path=/api/inventory/**
          # 示例：
          # 请求：http://localhost:8000/api/inventory/check?productId=1
          # 转发：http://inventory-service实例/api/inventory/check?productId=1
        
        # ========== 通知服务路由 ==========
        - id: notification-service
          uri: lb://notification-service
          predicates:
            - Path=/api/notifications/**
      
      # ========== 全局CORS跨域配置 ==========
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins: "*"      # 允许所有域名（生产环境应指定具体域名）
            allowed-methods: "*"      # 允许所有HTTP方法
            allowed-headers: "*"      # 允许所有请求头
            allow-credentials: true   # 允许携带Cookie

# ========== Actuator监控端点 ==========
management:
  endpoints:
    web:
      exposure:
        include: '*'  # 暴露所有监控端点
  # 访问：http://localhost:8000/actuator/health
  # 访问：http://localhost:8000/actuator/gateway/routes
```

### 3.3 启动类

```java
package com.demo.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关服务启动类
 * 
 * 功能：
 * 1. 作为系统统一入口
 * 2. 路由转发：将请求转发到具体的微服务
 * 3. 服务发现：从Nacos获取服务实例
 * 4. 负载均衡：自动在多个实例间分配请求
 * 5. 跨域处理：统一配置CORS
 */
@SpringBootApplication
@EnableDiscoveryClient  // 启用Nacos服务发现
public class GatewayServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
```

---

## 四、网关工作流程详解

### 4.1 完整请求流程

以创建订单为例：

```
┌──────────────────────────────────────────────────────────────┐
│                     完整请求流程                               │
└──────────────────────────────────────────────────────────────┘

🌐 前端发起请求
   └─► POST http://localhost:8000/api/orders
       Headers: Content-Type: application/json
       Body: {
         "customerId": 1,
         "productId": 100,
         "quantity": 2,
         "totalAmount": 199.8
       }

      │
      │ ① 请求到达网关
      ▼
┌─────────────────────────────────────────┐
│   Gateway :8000                          │
│                                          │
│  ② 路由匹配                              │
│     检查所有路由规则...                   │
│     ✅ 匹配成功！                         │
│     - id: order-service                 │
│     - predicate: /api/orders/**         │
│     - uri: lb://order-service           │
│                                          │
│  ③ CORS处理（Pre-Filter）                │
│     检查请求源...                         │
│     添加响应头：                          │
│     - Access-Control-Allow-Origin: *    │
│     - Access-Control-Allow-Methods: *   │
│                                          │
│  ④ 服务发现（从Nacos查询）                │
│     查询服务：order-service              │
│     返回实例列表：                        │
│     - 192.168.1.10:8001 (健康)          │
│     - 192.168.1.10:8002 (健康)          │
│                                          │
│  ⑤ 负载均衡（轮询）                       │
│     上次选择：8001                       │
│     本次选择：8002 ◄────────────         │
│                                          │
│  ⑥ 转发请求                              │
│     POST http://192.168.1.10:8002/api/orders
│     (原封不动转发请求体和请求头)          │
└──────────┬──────────────────────────────┘
           │
           │ ⑦ 请求到达目标服务
           ▼
┌─────────────────────────────────────────┐
│   Order Service :8002                    │
│                                          │
│  ⑧ 业务逻辑处理                           │
│     1. 保存订单到数据库                   │
│     2. 发送Kafka消息（扣减库存）          │
│     3. 发送Kafka消息（发送通知）          │
│                                          │
│  ⑨ 返回响应                              │
│     Status: 200 OK                      │
│     Body: {                             │
│       "orderId": 12345,                 │
│       "status": "PENDING",              │
│       "message": "订单创建成功"           │
│     }                                   │
└──────────┬──────────────────────────────┘
           │
           │ ⑩ 响应返回网关
           ▼
┌─────────────────────────────────────────┐
│   Gateway :8000                          │
│                                          │
│  ⑪ Post-Filter                          │
│     记录日志：                            │
│     - 请求路径：/api/orders              │
│     - 目标服务：order-service:8002       │
│     - 响应时间：120ms                    │
│     - 状态码：200                        │
│                                          │
│  ⑫ 返回给客户端                          │
└──────────┬──────────────────────────────┘
           │
           │ ⑬ 响应到达前端
           ▼
🌐 前端接收响应
   └─► Status: 200 OK
       Data: {
         "orderId": 12345,
         "status": "PENDING",
         "message": "订单创建成功"
       }
   └─► 页面显示：订单创建成功！
```

### 4.2 负载均衡详解

当订单服务有多个实例时：

```
Nacos服务列表：
  order-service
    ├─► Instance 1: 192.168.1.10:8001 (权重: 1.0, 健康)
    ├─► Instance 2: 192.168.1.10:8002 (权重: 1.0, 健康)
    └─► Instance 3: 192.168.1.10:8003 (权重: 2.0, 健康)

负载均衡策略（默认：轮询）：
  请求1 → 8001
  请求2 → 8002
  请求3 → 8003
  请求4 → 8001
  请求5 → 8002
  ...

权重策略（可配置）：
  8003的权重是其他实例的2倍
  请求1 → 8001
  请求2 → 8002
  请求3 → 8003 ◄── 更高概率
  请求4 → 8003 ◄── 更高概率
  请求5 → 8001
  ...

健康检查：
  当8002实例不健康时：
  ├─► Nacos标记为down
  ├─► Gateway自动剔除该实例
  └─► 请求只会转发到8001和8003

  当8002恢复健康时：
  ├─► Nacos标记为up
  ├─► Gateway自动加入该实例
  └─► 请求恢复转发到8001、8002、8003
```

### 4.3 服务发现机制

```
┌────────────────────────────────────────────────────────────┐
│                     服务发现流程                             │
└────────────────────────────────────────────────────────────┘

1️⃣ 服务启动注册
   Order Service启动 :8001
      │
      ├─► 读取配置：spring.application.name=order-service
      ├─► 连接Nacos：192.168.1.10:8848
      └─► 注册服务：
          - 服务名：order-service
          - IP：192.168.1.10
          - 端口：8001
          - 元数据：version=1.0, region=cn-east
      
   Nacos确认注册成功
      └─► 响应：注册成功，心跳间隔5秒

2️⃣ 心跳维持（保持注册状态）
   Order Service :8001
      ├─► 每5秒发送心跳到Nacos
      └─► Nacos响应：健康

   如果15秒未收到心跳：
      ├─► Nacos标记服务为"不健康"
      └─► Gateway不再转发请求到该实例

   如果30秒未收到心跳：
      ├─► Nacos剔除该实例
      └─► 服务列表中移除

3️⃣ 服务发现（Gateway查询服务）
   Gateway收到请求：/api/orders/123
      │
      ├─► 匹配路由：uri=lb://order-service
      │
      ├─► 查询Nacos：
      │   GET /nacos/v1/ns/instance/list?serviceName=order-service
      │
      └─► Nacos返回：
          {
            "hosts": [
              {
                "ip": "192.168.1.10",
                "port": 8001,
                "healthy": true,
                "weight": 1.0
              },
              {
                "ip": "192.168.1.10",
                "port": 8002,
                "healthy": true,
                "weight": 1.0
              }
            ]
          }

4️⃣ 本地缓存（提升性能）
   Gateway缓存服务列表：
      ├─► 首次查询：从Nacos获取 (耗时~10ms)
      ├─► 后续查询：从本地缓存获取 (耗时<1ms)
      └─► 缓存刷新：每30秒从Nacos更新一次

5️⃣ 服务下线
   Order Service :8001优雅关闭
      │
      ├─► 发送注销请求到Nacos
      ├─► Nacos立即移除该实例
      └─► Gateway下次刷新缓存时移除该实例
      
   如果服务异常崩溃（未发送注销）：
      ├─► Nacos等待30秒心跳超时
      └─► 自动剔除该实例
```

---

## 五、实现效果演示

### 5.1 启动效果

#### 第一步：启动Nacos

```bash
# 启动Nacos（单机模式）
cd nacos/bin
./startup.sh -m standalone

# 访问Nacos控制台
浏览器打开：http://localhost:8848/nacos
用户名：nacos
密码：nacos
```

#### 第二步：启动微服务

```bash
# 1. 启动网关
cd gateway-service
mvn spring-boot:run

# 控制台输出：
2024-11-20 10:00:00 INFO [main] c.d.g.GatewayServiceApplication : Started GatewayServiceApplication in 3.2 seconds
2024-11-20 10:00:00 INFO [main] o.s.c.g.r.RouteDefinitionRouteLocator : Loaded RoutePredicateFactory [Path]
2024-11-20 10:00:00 INFO [main] o.s.c.g.r.RouteDefinitionRouteLocator : Loaded Route: order-service [Path: /api/orders/**, URI: lb://order-service]
2024-11-20 10:00:01 INFO [main] c.a.c.n.r.NacosServiceRegistry : nacos registry, gateway-service 192.168.1.10:8000 register finished

✅ 网关启动成功！端口：8000

# 2. 启动订单服务
cd ../order-service
mvn spring-boot:run

# 控制台输出：
2024-11-20 10:00:30 INFO [main] c.d.o.OrderServiceApplication : Started OrderServiceApplication in 4.1 seconds
2024-11-20 10:00:31 INFO [main] c.a.c.n.r.NacosServiceRegistry : nacos registry, order-service 192.168.1.10:8001 register finished

✅ 订单服务启动成功！端口：8001

# 3. 启动库存服务
cd ../inventory-service
mvn spring-boot:run

✅ 库存服务启动成功！端口：8002

# 4. 启动通知服务
cd ../notification-service
mvn spring-boot:run

✅ 通知服务启动成功！端口：8003
```

#### 第三步：验证注册情况

打开Nacos控制台 → 服务管理 → 服务列表：

```
服务名称                 集群数量  实例数  健康实例数
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
gateway-service          1        1       1
order-service            1        1       1
inventory-service        1        1       1
notification-service     1        1       1

点击详情可以看到：
order-service
  └─► 实例列表：
      IP地址          端口   权重  健康状态
      192.168.1.10    8001   1.0   健康✅
```

### 5.2 功能测试

#### 1️⃣ 直接访问服务（不通过网关）

```bash
# 直接调用订单服务
curl http://localhost:8001/api/orders

# 响应：
{
  "data": [
    {
      "orderId": 1,
      "customerId": 101,
      "productId": 1001,
      "quantity": 2,
      "totalAmount": 199.8,
      "status": "COMPLETED"
    }
  ]
}

✅ 直接访问成功
```

#### 2️⃣ 通过网关访问（推荐方式）

```bash
# 通过网关调用订单服务
curl http://localhost:8000/api/orders

# 网关控制台日志：
2024-11-20 10:05:00 INFO [reactor-http-nio-2] o.s.c.g.h.RoutePredicateHandlerMapping : Mapping [Exchange: GET http://localhost:8000/api/orders] to Route{id='order-service', uri=lb://order-service, predicates=[Path: /api/orders/**]}
2024-11-20 10:05:00 INFO [reactor-http-nio-2] c.a.c.n.d.NacosNamingService : current ips:(1) [{"ip":"192.168.1.10","port":8001,"weight":1.0}]
2024-11-20 10:05:00 INFO [reactor-http-nio-2] o.s.c.l.core.RoundRobinLoadBalancer : Selected: 192.168.1.10:8001

# 响应：
{
  "data": [
    {
      "orderId": 1,
      "customerId": 101,
      "productId": 1001,
      "quantity": 2,
      "totalAmount": 199.8,
      "status": "COMPLETED"
    }
  ]
}

✅ 网关转发成功！
```

#### 3️⃣ 创建订单（完整流程）

```bash
# POST请求创建订单
curl -X POST http://localhost:8000/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 102,
    "productId": 1002,
    "quantity": 3,
    "totalAmount": 299.7
  }'

# 网关日志：
2024-11-20 10:10:00 INFO [reactor-http-nio-3] o.s.c.g.h.RoutePredicateHandlerMapping : Matched route order-service
2024-11-20 10:10:00 INFO [reactor-http-nio-3] o.s.c.l.core.RoundRobinLoadBalancer : Selected: 192.168.1.10:8001

# 订单服务日志：
2024-11-20 10:10:00 INFO [http-nio-8001-exec-1] c.d.o.c.OrderController : Creating order: {customerId=102, productId=1002, quantity=3}
2024-11-20 10:10:00 INFO [http-nio-8001-exec-1] c.d.o.s.OrderService : Order saved with ID: 12345
2024-11-20 10:10:00 INFO [http-nio-8001-exec-1] c.d.o.s.OrderService : Sent InventoryUpdateRequest to Kafka
2024-11-20 10:10:00 INFO [http-nio-8001-exec-1] c.d.o.s.OrderService : Sent OrderCreatedEvent to Kafka

# 库存服务日志（异步消费Kafka消息）：
2024-11-20 10:10:01 INFO [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] c.d.i.s.InventoryService : Received inventory update: productId=1002, quantity=-3
2024-11-20 10:10:01 INFO [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] c.d.i.s.InventoryService : Inventory updated successfully

# 通知服务日志（异步消费Kafka消息）：
2024-11-20 10:10:01 INFO [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] c.d.n.s.NotificationService : Received order event: orderId=12345
2024-11-20 10:10:01 INFO [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] c.d.n.s.NotificationService : Sent notification to RabbitMQ

# 响应：
{
  "orderId": 12345,
  "customerId": 102,
  "productId": 1002,
  "quantity": 3,
  "totalAmount": 299.7,
  "status": "PENDING",
  "createdAt": "2024-11-20T10:10:00"
}

✅ 完整流程执行成功！
   1. 网关路由转发 ✅
   2. 订单服务创建订单 ✅
   3. 库存服务扣减库存 ✅
   4. 通知服务发送通知 ✅
```

#### 4️⃣ 负载均衡测试

```bash
# 启动第二个订单服务实例（端口8004）
cd order-service
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8004

# Nacos现在有两个order-service实例：
order-service
  ├─► 192.168.1.10:8001
  └─► 192.168.1.10:8004

# 连续发送5次请求
for i in {1..5}; do
  curl http://localhost:8000/api/orders
done

# 网关日志（自动轮询）：
2024-11-20 10:15:00 INFO Selected: 192.168.1.10:8001 ◄── 请求1
2024-11-20 10:15:01 INFO Selected: 192.168.1.10:8004 ◄── 请求2
2024-11-20 10:15:02 INFO Selected: 192.168.1.10:8001 ◄── 请求3
2024-11-20 10:15:03 INFO Selected: 192.168.1.10:8004 ◄── 请求4
2024-11-20 10:15:04 INFO Selected: 192.168.1.10:8001 ◄── 请求5

✅ 负载均衡正常工作！请求均匀分配到两个实例
```

#### 5️⃣ 跨域测试（前端集成）

```javascript
// 前端代码（Vue3）
import axios from 'axios'

// 配置axios基础URL
const api = axios.create({
  baseURL: 'http://localhost:8000/api',  // 网关地址
  timeout: 5000
})

// 获取订单列表
async function getOrders() {
  try {
    const response = await api.get('/orders')
    console.log('订单列表:', response.data)
  } catch (error) {
    console.error('请求失败:', error)
  }
}

// 创建订单
async function createOrder() {
  try {
    const response = await api.post('/orders', {
      customerId: 103,
      productId: 1003,
      quantity: 1,
      totalAmount: 99.9
    })
    console.log('订单创建成功:', response.data)
  } catch (error) {
    console.error('创建失败:', error)
  }
}

// 执行
getOrders()
createOrder()
```

浏览器控制台输出：

```
[Network]
Request URL: http://localhost:8000/api/orders
Request Method: GET
Status Code: 200 OK

Response Headers:
  access-control-allow-origin: *  ◄── 网关自动添加，允许跨域
  access-control-allow-methods: *
  access-control-allow-headers: *
  content-type: application/json

✅ 跨域请求成功！无需每个服务单独配置CORS
```

### 5.3 监控查看

#### 查看网关路由信息

```bash
# 访问Actuator端点
curl http://localhost:8000/actuator/gateway/routes

# 响应：
[
  {
    "route_id": "order-service",
    "route_definition": {
      "id": "order-service",
      "predicates": [
        {
          "name": "Path",
          "args": {
            "pattern": "/api/orders/**"
          }
        }
      ],
      "uri": "lb://order-service",
      "order": 0
    },
    "order": 0
  },
  {
    "route_id": "inventory-service",
    "route_definition": {
      "id": "inventory-service",
      "predicates": [
        {
          "name": "Path",
          "args": {
            "pattern": "/api/inventory/**"
          }
        }
      ],
      "uri": "lb://inventory-service",
      "order": 0
    },
    "order": 0
  },
  {
    "route_id": "notification-service",
    "route_definition": {
      "id": "notification-service",
      "predicates": [
        {
          "name": "Path",
          "args": {
            "pattern": "/api/notifications/**"
          }
        }
      ],
      "uri": "lb://notification-service",
      "order": 0
    },
    "order": 0
  }
]
```

#### 查看网关健康状态

```bash
curl http://localhost:8000/actuator/health

# 响应：
{
  "status": "UP",
  "components": {
    "discoveryComposite": {
      "status": "UP",
      "components": {
        "discoveryClient": {
          "status": "UP",
          "details": {
            "services": [
              "gateway-service",
              "order-service",
              "inventory-service",
              "notification-service"
            ]
          }
        }
      }
    },
    "ping": {
      "status": "UP"
    },
    "refreshScope": {
      "status": "UP"
    }
  }
}
```

---

## 六、核心功能解析

### 6.1 统一入口

**没有网关：**

```
前端应用
  ├─► http://order-service:8001/api/orders      ❌ 需要知道订单服务地址
  ├─► http://inventory-service:8002/api/inventory ❌ 需要知道库存服务地址
  └─► http://notification-service:8003/api/notifications ❌ 需要知道通知服务地址

问题：
1. 前端需要维护所有服务的地址
2. 服务地址变更，前端需要重新部署
3. 无法统一处理鉴权、限流、日志
```

**使用网关：**

```
前端应用
  └─► http://gateway:8000/api/*  ✅ 只需要知道网关地址
      ├─► /api/orders/** → order-service
      ├─► /api/inventory/** → inventory-service
      └─► /api/notifications/** → notification-service

优势：
1. 前端只需要配置一个网关地址
2. 后端服务可以随意扩容、迁移、下线
3. 统一处理跨域、鉴权、限流、日志
4. 隔离内部服务，提高安全性
```

### 6.2 动态路由与服务发现

```yaml
# 静态路由（不推荐）
uri: http://192.168.1.10:8001  # 硬编码IP和端口
# 问题：
# - 服务实例增加/减少需要修改配置
# - 无法实现负载均衡
# - 实例故障无法自动切换

# 动态路由（推荐）✅
uri: lb://order-service  # 从Nacos动态获取服务实例
# 优势：
# - 服务实例自动发现
# - 自动负载均衡
# - 实例故障自动剔除
# - 无需修改配置
```

**工作原理：**

```
1. 服务启动时注册到Nacos
   order-service :8001 → Nacos
   order-service :8002 → Nacos
   order-service :8003 → Nacos

2. Gateway从Nacos查询服务列表
   Gateway → Nacos: 查询order-service
   Nacos → Gateway: [8001, 8002, 8003]

3. Gateway选择一个实例转发请求
   请求1 → 8001
   请求2 → 8002
   请求3 → 8003
   ...

4. 实例故障自动剔除
   8002故障 → Nacos标记为不健康
   Gateway下次刷新缓存时移除8002
   后续请求只转发到8001和8003

5. 实例恢复自动加入
   8002恢复 → Nacos标记为健康
   Gateway下次刷新缓存时加入8002
   后续请求恢复转发到8001、8002、8003
```

### 6.3 负载均衡

```
┌──────────────────────────────────────────────────────────┐
│               负载均衡策略                                 │
└──────────────────────────────────────────────────────────┘

1️⃣ 轮询（Round Robin，默认）
   实例：[A, B, C]
   请求序列：A → B → C → A → B → C → ...
   
   优点：请求分配均匀
   缺点：不考虑实例性能差异

2️⃣ 随机（Random）
   实例：[A, B, C]
   请求序列：B → A → C → B → A → ...（随机选择）
   
   优点：实现简单
   缺点：可能分配不均

3️⃣ 加权轮询（Weighted Round Robin）
   实例：[A(权重1), B(权重1), C(权重2)]
   请求序列：A → B → C → C → A → B → C → C → ...
   
   优点：可以根据实例性能分配不同权重
   应用：新老机器混合部署

4️⃣ 最少连接（Least Connections）
   实例：[A(连接数10), B(连接数5), C(连接数8)]
   选择：B（连接数最少）
   
   优点：避免单个实例过载
   缺点：需要维护连接数状态
```

**本项目使用的轮询策略：**

```java
// Spring Cloud LoadBalancer默认使用轮询
@Bean
public ReactorLoadBalancer<ServiceInstance> reactorServiceInstanceLoadBalancer(
    Environment environment,
    LoadBalancerClientFactory loadBalancerClientFactory) {
    String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
    return new RoundRobinLoadBalancer(
        loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class),
        name);
}

// 轮询算法伪代码
class RoundRobinLoadBalancer {
    private AtomicInteger position = new AtomicInteger(0);
    
    public ServiceInstance choose(List<ServiceInstance> instances) {
        if (instances.isEmpty()) {
            return null;
        }
        int pos = Math.abs(position.incrementAndGet());
        return instances.get(pos % instances.size());
    }
}
```

### 6.4 跨域处理（CORS）

**为什么需要配置CORS？**

```
┌──────────────────────────────────────────────────────────┐
│                    跨域问题                                │
└──────────────────────────────────────────────────────────┘

前端应用运行在：http://localhost:5173 (Vue3开发服务器)
API服务运行在：  http://localhost:8000 (网关)

浏览器同源策略：
  ├─► 协议相同：http = http ✅
  ├─► 域名相同：localhost = localhost ✅
  └─► 端口相同：5173 ≠ 8000 ❌  跨域！

浏览器拦截请求：
  ❌ Access to XMLHttpRequest at 'http://localhost:8000/api/orders' 
     from origin 'http://localhost:5173' has been blocked by CORS policy: 
     No 'Access-Control-Allow-Origin' header is present on the requested resource.
```

**网关统一配置CORS（推荐）：**

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins: "*"       # 允许的源（生产环境应指定具体域名）
            allowed-methods: "*"       # 允许的HTTP方法
            allowed-headers: "*"       # 允许的请求头
            allow-credentials: true    # 允许携带Cookie
            max-age: 3600             # 预检请求缓存时间（秒）

# 网关会自动在响应中添加以下头：
# Access-Control-Allow-Origin: *
# Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
# Access-Control-Allow-Headers: *
# Access-Control-Allow-Credentials: true
```

**对比每个服务单独配置：**

```java
// ❌ 不推荐：每个服务都要配置
@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                    .allowedOrigins("*")
                    .allowedMethods("*");
            }
        };
    }
}
// 问题：
// 1. 每个服务都要写重复代码
// 2. 配置不一致容易出问题
// 3. 维护成本高

// ✅ 推荐：网关统一配置
// 优势：
// 1. 一处配置，全局生效
// 2. 后端服务无需关心跨域问题
// 3. 配置统一，易于维护
```

### 6.5 路由转发机制

**路由匹配优先级：**

```yaml
routes:
  # 优先级1：最具体的路径
  - id: order-detail
    uri: lb://order-service
    predicates:
      - Path=/api/orders/{id}  # 精确匹配
    order: 1
  
  # 优先级2：通配符路径
  - id: order-service
    uri: lb://order-service
    predicates:
      - Path=/api/orders/**    # 通配符匹配
    order: 2
  
  # 优先级3：默认路由（兜底）
  - id: fallback
    uri: lb://fallback-service
    predicates:
      - Path=/**              # 匹配所有
    order: 999

# order值越小，优先级越高
```

**路径重写：**

```yaml
routes:
  - id: order-service
    uri: lb://order-service
    predicates:
      - Path=/api/orders/**
    filters:
      - StripPrefix=1  # 去掉第一层路径前缀

# 示例：
# 请求：http://gateway:8000/api/orders/123
# 转发：http://order-service:8001/orders/123  (去掉了/api前缀)

# 如果不配置StripPrefix：
# 请求：http://gateway:8000/api/orders/123
# 转发：http://order-service:8001/api/orders/123  (保留完整路径)
```

**本项目的路径设计：**

```
网关层路径：
  /api/orders/**
  /api/inventory/**
  /api/notifications/**

服务层路径（保持一致）：
  Order Service:
    @RequestMapping("/api/orders")
    class OrderController { ... }
  
  Inventory Service:
    @RequestMapping("/api/inventory")
    class InventoryController { ... }

优势：
  ✅ 不需要StripPrefix，路径保持一致
  ✅ 前端、网关、服务的路径映射清晰
  ✅ 便于调试和维护
```

---

## 七、实际应用场景

### 7.1 微服务架构中的网关位置

```
┌────────────────────────────────────────────────────────────────┐
│                         完整架构图                              │
└────────────────────────────────────────────────────────────────┘

                  Internet
                     │
                     ▼
        ┌────────────────────────┐
        │   Nginx (反向代理)      │
        │   :80 / :443           │  ◄── 处理静态资源、SSL
        └────────┬───────────────┘
                 │
                 ├──► 静态资源：index.html, *.js, *.css
                 │
                 └──► API请求：/api/*
                      │
                      ▼
        ┌────────────────────────┐
        │  Spring Cloud Gateway  │
        │        :8000           │  ◄── 路由、服务发现、负载均衡
        └────────┬───────────────┘
                 │
    ┌────────────┼────────────┐
    │            │            │
    ▼            ▼            ▼
┌────────┐  ┌────────┐  ┌────────┐
│订单服务│  │库存服务│  │通知服务│
│ :8001 │  │ :8002 │  │ :8003 │
└────────┘  └────────┘  └────────┘
    │            │            │
    └────────────┼────────────┘
                 │
    ┌────────────┼────────────┐
    │            │            │
    ▼            ▼            ▼
┌────────┐  ┌────────┐  ┌────────┐
│ Nacos  │  │PostgreSQL│  │ Kafka │
│ :8848  │  │ :5432  │  │ :9092 │
└────────┘  └────────┘  └────────┘
```

### 7.2 真实场景案例

#### 场景1：电商大促（双11）

```
挑战：订单服务流量暴增10倍

解决方案：
1. 扩容订单服务实例
   order-service :8001
   order-service :8002
   order-service :8003
   order-service :8004  ← 新增
   order-service :8005  ← 新增
   order-service :8006  ← 新增

2. 网关自动发现新实例
   Gateway从Nacos获取最新实例列表
   自动将请求分配到6个实例

3. 流量平稳过渡
   每个实例承担16.7%的流量
   单机QPS从10000降低到1667

4. 活动结束后缩容
   停止额外实例
   Gateway自动剔除已下线实例
   恢复正常配置

✅ 全程无需修改配置，零停机扩缩容
```

#### 场景2：灰度发布（金丝雀部署）

```
需求：订单服务发布新版本v2.0，需要灰度验证

步骤：
1. 部署一个v2.0实例
   order-service v1.0 :8001 (权重9)
   order-service v1.0 :8002 (权重9)
   order-service v2.0 :8003 (权重1)  ← 新版本

2. 配置权重路由
   spring:
     cloud:
       nacos:
         discovery:
           metadata:
             version: v2.0
             weight: 0.1  # 10%流量

3. 观察v2.0表现
   监控指标：成功率、响应时间、错误率
   如果正常 → 逐步增加权重
   如果异常 → 立即下线v2.0

4. 逐步切换流量
   v2.0权重: 10% → 30% → 50% → 100%

5. 全量发布
   所有实例升级到v2.0
   下线v1.0实例

✅ 风险可控，问题可快速回滚
```

#### 场景3：多环境路由

```
需求：同一网关根据请求头路由到不同环境

配置：
routes:
  # 测试环境路由
  - id: order-service-test
    uri: lb://order-service-test
    predicates:
      - Path=/api/orders/**
      - Header=X-Env, test  # 请求头包含X-Env: test
    order: 1
  
  # 生产环境路由
  - id: order-service-prod
    uri: lb://order-service-prod
    predicates:
      - Path=/api/orders/**
    order: 2

使用：
# 访问测试环境
curl -H "X-Env: test" http://gateway:8000/api/orders
→ 路由到order-service-test

# 访问生产环境
curl http://gateway:8000/api/orders
→ 路由到order-service-prod

✅ 同一网关支持多环境，便于测试
```

### 7.3 性能优化

#### 优化1：本地缓存服务列表

```yaml
spring:
  cloud:
    loadbalancer:
      cache:
        enabled: true         # 启用缓存
        ttl: 30s             # 缓存30秒
        capacity: 256        # 缓存容量

# 效果：
# 首次查询：从Nacos获取 (10ms)
# 后续查询：从缓存获取 (<1ms)
# 每30秒刷新一次
```

#### 优化2：连接池配置

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        pool:
          max-connections: 500        # 最大连接数
          max-pending-acquires: 1000  # 最大等待连接数
        connect-timeout: 3000         # 连接超时3秒
        response-timeout: 10s         # 响应超时10秒

# 效果：
# 高并发下复用连接，减少TCP握手开销
```

#### 优化3：启用HTTP/2

```yaml
server:
  http2:
    enabled: true  # 启用HTTP/2

# 效果：
# - 多路复用：一个连接处理多个请求
# - 头部压缩：减少数据传输
# - 服务器推送：主动推送资源
```

### 7.4 安全增强

#### 方案1：统一鉴权

```java
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 白名单：登录、注册等接口无需鉴权
        if (isWhitelist(request.getPath().value())) {
            return chain.filter(exchange);
        }
        
        // 获取Token
        String token = request.getHeaders().getFirst("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        
        // 验证Token
        try {
            Claims claims = jwtUtil.parseToken(token.substring(7));
            // 将用户信息添加到请求头，传递给后端服务
            ServerHttpRequest newRequest = request.mutate()
                .header("X-User-Id", claims.get("userId").toString())
                .header("X-Username", claims.get("username").toString())
                .build();
            return chain.filter(exchange.mutate().request(newRequest).build());
        } catch (Exception e) {
            return unauthorized(exchange);
        }
    }
    
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
    
    @Override
    public int getOrder() {
        return -100;  // 优先级高，第一个执行
    }
}
```

#### 方案2：限流保护

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100  # 每秒生成100个令牌
                redis-rate-limiter.burstCapacity: 200  # 令牌桶容量200
                key-resolver: "#{@ipKeyResolver}"      # 按IP限流

# 效果：
# 单个IP每秒最多100次请求
# 短时突发最多200次请求
# 超过限制返回429 Too Many Requests
```

---

## 八、总结

### 8.1 核心价值

| 功能 | 价值 | 本项目体现 |
|------|------|----------|
| **统一入口** | 前端只需配置一个地址 | `http://localhost:8000/api/*` |
| **服务发现** | 自动发现服务实例 | 集成Nacos，`lb://order-service` |
| **负载均衡** | 自动分配请求到多个实例 | 轮询策略，流量均匀分配 |
| **跨域处理** | 统一配置CORS | 网关配置`globalcors` |
| **路由转发** | 智能路由匹配 | `/api/orders/**` → order-service |
| **零配置扩缩容** | 服务实例动态上下线 | 启动/停止服务，网关自动感知 |

### 8.2 本项目网关实现的效果

✅ **统一访问入口**
- 前端只需要知道网关地址 `http://localhost:8000`
- 所有API请求统一前缀 `/api/*`

✅ **自动服务发现**
- 服务启动自动注册到Nacos
- 网关自动发现并路由到健康实例

✅ **自动负载均衡**
- 多个服务实例自动轮询
- 无需手动配置负载均衡器

✅ **零停机扩缩容**
- 启动新实例立即生效
- 停止实例自动剔除

✅ **统一跨域处理**
- 前端无跨域问题
- 后端服务无需配置CORS

✅ **高可用保障**
- 服务实例故障自动切换
- 健康检查机制

✅ **监控与运维**
- Actuator端点查看路由信息
- 实时监控服务健康状态

### 8.3 适用场景

| 场景 | 是否适合Gateway | 原因 |
|------|----------------|------|
| 微服务架构 | ✅ 非常适合 | 天然集成Spring Cloud生态 |
| 服务发现 | ✅ 非常适合 | 与Nacos无缝集成 |
| 动态路由 | ✅ 非常适合 | 支持配置热更新 |
| 高并发场景 | ✅ 适合 | 基于Reactor异步非阻塞 |
| 复杂路由规则 | ✅ 适合 | 丰富的Predicate和Filter |
| 纯静态网站 | ❌ 不适合 | 用Nginx更轻量 |
| 非Spring生态 | ❌ 不适合 | 用Kong/APISIX更通用 |

---

## 九、进阶学习

### 9.1 自定义过滤器

```java
@Component
public class CustomGatewayFilter implements GlobalFilter, Ordered {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 请求前处理
        ServerHttpRequest request = exchange.getRequest();
        System.out.println("请求路径: " + request.getPath());
        System.out.println("请求方法: " + request.getMethod());
        
        // 添加自定义请求头
        ServerHttpRequest newRequest = request.mutate()
            .header("X-Request-Id", UUID.randomUUID().toString())
            .header("X-Request-Time", String.valueOf(System.currentTimeMillis()))
            .build();
        
        // 继续执行
        return chain.filter(exchange.mutate().request(newRequest).build())
            .then(Mono.fromRunnable(() -> {
                // 响应后处理
                ServerHttpResponse response = exchange.getResponse();
                System.out.println("响应状态码: " + response.getStatusCode());
            }));
    }
    
    @Override
    public int getOrder() {
        return 0;
    }
}
```

### 9.2 动态路由

```java
@Service
public class DynamicRouteService {
    
    @Autowired
    private RouteDefinitionWriter routeDefinitionWriter;
    
    @Autowired
    private ApplicationEventPublisher publisher;
    
    /**
     * 动态添加路由
     */
    public void addRoute(String routeId, String uri, String path) {
        RouteDefinition definition = new RouteDefinition();
        definition.setId(routeId);
        definition.setUri(URI.create(uri));
        
        // 设置断言
        PredicateDefinition predicate = new PredicateDefinition();
        predicate.setName("Path");
        predicate.addArg("pattern", path);
        definition.setPredicates(Collections.singletonList(predicate));
        
        // 保存路由
        routeDefinitionWriter.save(Mono.just(definition)).subscribe();
        
        // 发布刷新事件
        publisher.publishEvent(new RefreshRoutesEvent(this));
    }
    
    /**
     * 动态删除路由
     */
    public void deleteRoute(String routeId) {
        routeDefinitionWriter.delete(Mono.just(routeId)).subscribe();
        publisher.publishEvent(new RefreshRoutesEvent(this));
    }
}
```

### 9.3 熔断降级

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
          filters:
            - name: CircuitBreaker
              args:
                name: orderCircuitBreaker
                fallbackUri: forward:/fallback/orders

# 降级处理
@RestController
@RequestMapping("/fallback")
public class FallbackController {
    
    @GetMapping("/orders")
    public ResponseEntity<String> orderFallback() {
        return ResponseEntity.ok("订单服务暂时不可用，请稍后再试");
    }
}
```

---

## 📚 参考资料

- [Spring Cloud Gateway 官方文档](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)
- [Nacos 官方文档](https://nacos.io/zh-cn/docs/quick-start.html)
- [Spring Cloud Alibaba 官方文档](https://spring-cloud-alibaba-group.github.io/github-pages/hoxton/zh-cn/index.html)
- [Reactor 官方文档](https://projectreactor.io/docs/core/release/reference/)

---

**完成时间：** 2024-11-20  
**作者：** Demo Team  
**版本：** 1.0.0

