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

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    private static final String ORDER_CACHE_PREFIX = "order:";
    private static final String TOPIC_ORDER_CREATED = "order-created";
    
    /**
     * 创建订单
     */
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        log.info("创建订单: userId={}, productId={}", request.getUserId(), request.getProductId());
        
        // 1. 生成订单号
        String orderNo = generateOrderNo();
        
        // 2. 创建订单
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(request.getUserId());
        order.setProductId(request.getProductId());
        order.setProductName(request.getProductName());
        order.setQuantity(request.getQuantity());
        order.setTotalAmount(request.getTotalAmount());
        order.setStatus("PENDING");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        
        Order savedOrder = orderRepository.save(order);
        
        // 3. 缓存订单到Redis (30分钟)
        try {
            String cacheKey = ORDER_CACHE_PREFIX + orderNo;
            String orderJson = objectMapper.writeValueAsString(savedOrder);
            redisTemplate.opsForValue().set(cacheKey, orderJson, 30, TimeUnit.MINUTES);
            log.info("订单已缓存到Redis: {}", orderNo);
        } catch (Exception e) {
            log.error("缓存订单失败", e);
        }
        
        // 4. 发送Kafka消息
        try {
            kafkaTemplate.send(TOPIC_ORDER_CREATED, orderNo, savedOrder);
            log.info("订单创建消息已发送到Kafka: {}", orderNo);
        } catch (Exception e) {
            log.error("发送Kafka消息失败", e);
        }
        
        return toResponse(savedOrder);
    }
    
    /**
     * 查询订单 (先查Redis,再查数据库)
     */
    public OrderResponse getOrder(String orderNo) {
        log.info("查询订单: {}", orderNo);
        
        // 1. 先从Redis查询
        String cacheKey = ORDER_CACHE_PREFIX + orderNo;
        try {
            String cachedOrder = redisTemplate.opsForValue().get(cacheKey);
            if (cachedOrder != null) {
                log.info("从Redis缓存获取订单: {}", orderNo);
                Order order = objectMapper.readValue(cachedOrder, Order.class);
                return toResponse(order);
            }
        } catch (Exception e) {
            log.error("从Redis读取失败", e);
        }
        
        // 2. Redis中没有,从数据库查询
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new RuntimeException("订单不存在: " + orderNo));
        
        // 3. 写回Redis
        try {
            String orderJson = objectMapper.writeValueAsString(order);
            redisTemplate.opsForValue().set(cacheKey, orderJson, 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("写入Redis失败", e);
        }
        
        return toResponse(order);
    }
    
    /**
     * 查询用户订单列表
     */
    public List<OrderResponse> getUserOrders(Long userId) {
        log.info("查询用户订单: userId={}", userId);
        List<Order> orders = orderRepository.findByUserId(userId);
        return orders.stream().map(this::toResponse).collect(Collectors.toList());
    }
    
    private String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
    }
    
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

