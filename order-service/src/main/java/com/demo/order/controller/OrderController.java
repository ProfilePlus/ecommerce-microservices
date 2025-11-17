package com.demo.order.controller;

import com.demo.order.dto.OrderRequest;
import com.demo.order.dto.OrderResponse;
import com.demo.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    
    private final OrderService orderService;
    
    /**
     * 创建订单
     */
    @PostMapping
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        return orderService.createOrder(request);
    }
    
    /**
     * 查询订单
     */
    @GetMapping("/{orderNo}")
    public OrderResponse getOrder(@PathVariable String orderNo) {
        return orderService.getOrder(orderNo);
    }
    
    /**
     * 查询用户订单列表
     */
    @GetMapping("/user/{userId}")
    public List<OrderResponse> getUserOrders(@PathVariable Long userId) {
        return orderService.getUserOrders(userId);
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public String health() {
        return "Order Service is running!";
    }
}

