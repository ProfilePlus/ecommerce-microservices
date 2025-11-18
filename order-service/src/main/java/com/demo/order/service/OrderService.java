package com.demo.order.service;

import com.demo.order.dto.OrderRequest;
import com.demo.order.dto.OrderResponse;
import com.demo.order.entity.Order;
import com.demo.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订单业务服务类
 * 
 * 功能说明：
 * 订单业务的核心实现，包括订单创建、查询等功能
 * 
 * 技术亮点：
 * 1. 缓存策略：使用Redis缓存热点订单数据，提高查询性能
 * 2. 异步消息：通过Kafka发送订单创建消息，实现服务解耦
 * 3. 事务管理：使用@Transactional保证数据一致性
 * 4. 对象映射：使用Jackson进行JSON序列化和反序列化
 * 
 * 业务流程：
 * 创建订单 -> 保存数据库 -> 缓存Redis -> 发送Kafka -> 库存服务消费 -> 扣减库存 -> 发送通知
 * 
 * @author demo
 * @version 1.0.0
 */
@Slf4j  // Lombok注解：自动生成日志对象log
@Service  // 标识这是一个服务层组件，由Spring容器管理
@RequiredArgsConstructor  // Lombok注解：自动生成包含final字段的构造函数
public class OrderService {
    
    /**
     * 订单数据访问层
     * 用于操作订单数据库表
     */
    private final OrderRepository orderRepository;
    
    /**
     * Kafka消息发送模板
     * 用于发送订单创建消息到Kafka队列
     */
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Redis字符串操作模板
     * 用于缓存订单数据，提高查询性能
     */
    private final StringRedisTemplate redisTemplate;
    
    /**
     * JSON对象映射器
     * 用于Java对象与JSON字符串之间的相互转换
     */
    private final ObjectMapper objectMapper;
    
    /**
     * Redis缓存键前缀
     * 格式：order:订单号
     * 示例：order:ORD1700000000000abc12345
     */
    private static final String ORDER_CACHE_PREFIX = "order:";
    
    /**
     * Kafka主题名称 - 订单创建消息
     * 库存服务会监听此主题，接收订单创建消息
     */
    private static final String TOPIC_ORDER_CREATED = "order-created";
    
    /**
     * 创建订单（核心业务方法）
     * 
     * 业务流程：
     * 1. 生成全局唯一的订单号
     * 2. 构建订单对象并保存到数据库
     * 3. 将订单数据缓存到Redis（30分钟过期）
     * 4. 发送订单创建消息到Kafka
     * 5. 返回订单信息给前端
     * 
     * 事务说明：
     * @Transactional保证数据库操作的原子性
     * 如果保存订单失败，会自动回滚
     * 
     * 异常处理：
     * - 缓存失败不影响主流程，只记录错误日志
     * - Kafka发送失败不影响主流程，只记录错误日志
     * 
     * @param request 订单请求对象，包含用户ID、商品信息、数量、金额等
     * @return OrderResponse 订单响应对象，包含订单号、状态等信息
     */
    @Transactional  // 开启事务管理，保证数据一致性
    public OrderResponse createOrder(OrderRequest request) {
        log.info("创建订单: userId={}, productId={}", request.getUserId(), request.getProductId());
        
        // 1. 生成订单号
        // 格式：ORD + 13位时间戳 + 8位随机字符
        // 保证全局唯一性
        String orderNo = generateOrderNo();
        
        // 2. 创建订单实体对象
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(request.getUserId());
        order.setProductId(request.getProductId());
        order.setProductName(request.getProductName());
        order.setQuantity(request.getQuantity());
        order.setTotalAmount(request.getTotalAmount());
        order.setStatus("PENDING");  // 初始状态为待处理
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        
        // 2.1 保存订单到数据库
        Order savedOrder = orderRepository.save(order);
        
        // 3. 缓存订单到Redis (30分钟过期)
        // 目的：提高后续查询性能，避免频繁访问数据库
        try {
            String cacheKey = ORDER_CACHE_PREFIX + orderNo;
            String orderJson = objectMapper.writeValueAsString(savedOrder);
            redisTemplate.opsForValue().set(cacheKey, orderJson, 30, TimeUnit.MINUTES);
            log.info("订单已缓存到Redis: {}", orderNo);
        } catch (Exception e) {
            // 缓存失败不影响主流程，只记录日志
            log.error("缓存订单失败", e);
        }
        
        // 4. 发送Kafka消息
        // 通知库存服务扣减库存
        // 使用异步消息队列，实现服务解耦
        try {
            kafkaTemplate.send(TOPIC_ORDER_CREATED, orderNo, savedOrder);
            log.info("订单创建消息已发送到Kafka: {}", orderNo);
        } catch (Exception e) {
            // Kafka发送失败不影响主流程，只记录日志
            // 实际项目中应该有补偿机制（如定时任务重发）
            log.error("发送Kafka消息失败", e);
        }
        
        // 5. 转换为响应对象并返回
        return toResponse(savedOrder);
    }
    
    /**
     * 查询订单详情
     * 
     * 查询策略（缓存穿透预防）：
     * 1. 先从Redis缓存查询（快速，毫秒级）
     * 2. 缓存命中则直接返回
     * 3. 缓存未命中则从数据库查询
     * 4. 查询结果写回Redis缓存
     * 
     * 性能优化：
     * - 使用Redis缓存，减少数据库压力
     * - 缓存过期时间30分钟，平衡内存占用和性能
     * 
     * 异常处理：
     * - Redis读取失败，降级到数据库查询
     * - 订单不存在，抛出RuntimeException
     * 
     * @param orderNo 订单号，全局唯一标识
     * @return OrderResponse 订单详情
     * @throws RuntimeException 订单不存在时抛出
     */
    public OrderResponse getOrder(String orderNo) {
        log.info("查询订单: {}", orderNo);
        
        // 1. 先从Redis查询（一级缓存）
        String cacheKey = ORDER_CACHE_PREFIX + orderNo;
        try {
            String cachedOrder = redisTemplate.opsForValue().get(cacheKey);
            if (cachedOrder != null) {
                // 缓存命中，直接返回
                log.info("从Redis缓存获取订单: {}", orderNo);
                Order order = objectMapper.readValue(cachedOrder, Order.class);
                return toResponse(order);
            }
        } catch (Exception e) {
            // Redis读取失败，降级到数据库查询
            log.error("从Redis读取失败", e);
        }
        
        // 2. Redis中没有，从数据库查询
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new RuntimeException("订单不存在: " + orderNo));
        
        // 3. 写回Redis（缓存预热）
        try {
            String orderJson = objectMapper.writeValueAsString(order);
            redisTemplate.opsForValue().set(cacheKey, orderJson, 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            // 写入Redis失败不影响主流程
            log.error("写入Redis失败", e);
        }
        
        return toResponse(order);
    }
    
    /**
     * 查询用户订单列表
     * 
     * 功能说明：
     * 查询指定用户的所有订单
     * 
     * 数据来源：
     * 直接从数据库查询，不使用缓存
     * 
     * 优化建议：
     * 1. 添加分页功能，避免一次查询过多数据
     * 2. 添加订单状态筛选
     * 3. 添加时间范围筛选
     * 4. 考虑使用Redis缓存用户最近订单列表
     * 
     * @param userId 用户ID
     * @return List<OrderResponse> 订单列表，按创建时间倒序
     */
    public List<OrderResponse> getUserOrders(Long userId) {
        log.info("查询用户订单: userId={}", userId);
        
        // 从数据库查询用户所有订单
        List<Order> orders = orderRepository.findByUserId(userId);
        
        // 使用Stream API将Order列表转换为OrderResponse列表
        return orders.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * 生成订单号（私有方法）
     * 
     * 订单号规则：
     * - 前缀：ORD（Order的缩写）
     * - 时间戳：13位毫秒级时间戳，保证时间唯一性
     * - 随机码：8位随机字符，增强唯一性
     * 
     * 示例：ORD1700000000000abc12345
     * 
     * 特点：
     * 1. 全局唯一
     * 2. 包含时间信息，方便排序
     * 3. 可读性好
     * 
     * @return String 订单号
     */
    private String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * 实体对象转响应对象（私有方法）
     * 
     * 功能说明：
     * 将数据库实体Order转换为前端响应对象OrderResponse
     * 
     * 转换目的：
     * 1. 隐藏数据库ID等敏感信息
     * 2. 只返回前端需要的字段
     * 3. 符合前后端分离的设计原则
     * 
     * @param order 订单实体对象
     * @return OrderResponse 订单响应对象
     */
    private OrderResponse toResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setOrderNo(order.getOrderNo());
        response.setUserId(order.getUserId());
        response.setProductId(order.getProductId());
        response.setProductName(order.getProductName());
        response.setQuantity(order.getQuantity());
        response.setTotalAmount(order.getTotalAmount());
        response.setStatus(order.getStatus());
        response.setCreateTime(order.getCreateTime());
        return response;
    }
}

