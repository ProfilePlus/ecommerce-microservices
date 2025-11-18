package com.demo.order.controller;

import com.demo.order.dto.OrderRequest;
import com.demo.order.dto.OrderResponse;
import com.demo.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单控制器
 * 
 * 功能说明：
 * 提供订单相关的RESTful API接口
 * 
 * 基础路径：/api/orders
 * 
 * 接口列表：
 * 1. POST /api/orders - 创建订单
 * 2. GET /api/orders/{orderNo} - 查询订单详情
 * 3. GET /api/orders/user/{userId} - 查询用户订单列表
 * 4. GET /api/orders/health - 健康检查
 * 
 * 访问方式：
 * - 通过网关访问: http://gateway:8000/api/orders/...
 * - 直接访问: http://order-service:8081/api/orders/...
 * 
 * 扩展方向：
 * 1. 添加参数校验（@Valid、@Validated）
 * 2. 添加统一异常处理（@ControllerAdvice）
 * 3. 添加接口权限控制（@PreAuthorize）
 * 4. 添加接口限流（@RateLimiter）
 * 5. 添加接口文档（Swagger、OpenAPI）
 * 6. 添加分页查询功能（Pageable）
 * 
 * @author demo
 * @version 1.0.0
 */
@RestController  // 标识这是一个REST控制器，自动将返回值序列化为JSON
@RequestMapping("/api/orders")  // 定义控制器的基础路径
@RequiredArgsConstructor  // Lombok注解：自动生成包含final字段的构造函数，用于依赖注入
public class OrderController {
    
    /**
     * 订单服务
     * final字段，通过构造函数注入，保证不可变性
     */
    private final OrderService orderService;
    
    /**
     * 创建订单接口
     * 
     * 接口地址：POST /api/orders
     * 请求体：OrderRequest JSON
     * 响应：OrderResponse JSON
     * 
     * 业务流程：
     * 1. 接收前端提交的订单请求
     * 2. 调用OrderService创建订单
     * 3. 保存订单到数据库
     * 4. 发送订单消息到Kafka
     * 5. 返回订单信息
     * 
     * 示例请求：
     * POST /api/orders
     * {
     *   "userId": 1,
     *   "productId": 100,
     *   "productName": "iPhone 15 Pro",
     *   "quantity": 2,
     *   "totalAmount": 19998.00
     * }
     * 
     * @param request 订单请求对象
     * @return OrderResponse 订单响应对象，包含订单号、状态等信息
     */
    @PostMapping
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        return orderService.createOrder(request);
    }
    
    /**
     * 查询订单详情接口
     * 
     * 接口地址：GET /api/orders/{orderNo}
     * 路径参数：orderNo - 订单号
     * 响应：OrderResponse JSON
     * 
     * 查询策略：
     * 1. 先从Redis缓存查询（快速）
     * 2. 缓存未命中则从数据库查询
     * 3. 查询结果写回Redis缓存
     * 
     * 示例请求：
     * GET /api/orders/ORD1700000000000abc12345
     * 
     * @param orderNo 订单号
     * @return OrderResponse 订单详情
     * @throws RuntimeException 订单不存在时抛出异常
     */
    @GetMapping("/{orderNo}")
    public OrderResponse getOrder(@PathVariable String orderNo) {
        return orderService.getOrder(orderNo);
    }
    
    /**
     * 查询用户订单列表接口
     * 
     * 接口地址：GET /api/orders/user/{userId}
     * 路径参数：userId - 用户ID
     * 响应：List<OrderResponse> JSON数组
     * 
     * 功能说明：
     * 查询指定用户的所有订单，按创建时间倒序排列
     * 
     * 示例请求：
     * GET /api/orders/user/1
     * 
     * 优化建议：
     * 1. 添加分页功能，避免数据量过大
     * 2. 添加订单状态筛选
     * 3. 添加时间范围筛选
     * 
     * @param userId 用户ID
     * @return List<OrderResponse> 订单列表
     */
    @GetMapping("/user/{userId}")
    public List<OrderResponse> getUserOrders(@PathVariable Long userId) {
        return orderService.getUserOrders(userId);
    }
    
    /**
     * 健康检查接口
     * 
     * 接口地址：GET /api/orders/health
     * 响应：String 文本信息
     * 
     * 功能说明：
     * 用于服务健康检查和监控
     * 
     * 使用场景：
     * 1. Kubernetes的liveness和readiness探针
     * 2. 负载均衡器的健康检查
     * 3. 监控系统的可用性检测
     * 
     * @return String 服务状态信息
     */
    @GetMapping("/health")
    public String health() {
        return "Order Service is running!";
    }
}

