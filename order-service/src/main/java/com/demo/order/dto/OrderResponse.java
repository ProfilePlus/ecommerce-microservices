package com.demo.order.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单响应DTO（数据传输对象）
 * 
 * 功能说明：
 * 用于返回订单信息给前端或客户端
 * 
 * 使用场景：
 * 1. POST /api/orders 创建订单后的响应
 * 2. GET /api/orders/{orderNo} 查询订单详情的响应
 * 3. GET /api/orders/user/{userId} 查询用户订单列表的响应
 * 
 * 设计考虑：
 * 1. 不包含敏感信息（如数据库ID）
 * 2. 字段名称友好，便于前端使用
 * 3. 包含必要的业务信息，方便前端展示
 * 
 * @author demo
 * @version 1.0.0
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class OrderResponse {
    
    /**
     * 订单号
     * 业务主键，用于订单追踪和查询
     */
    private String orderNo;
    
    /**
     * 用户ID
     * 标识订单所属用户
     */
    private Long userId;
    
    /**
     * 商品ID
     * 标识购买的商品
     */
    private Long productId;
    
    /**
     * 商品名称
     * 商品的展示名称
     */
    private String productName;
    
    /**
     * 购买数量
     * 订单中商品的数量
     */
    private Integer quantity;
    
    /**
     * 订单总金额
     * 订单的总价，单位：元
     */
    private BigDecimal totalAmount;
    
    /**
     * 订单状态
     * PENDING: 待处理
     * PROCESSING: 处理中
     * COMPLETED: 已完成
     * CANCELLED: 已取消
     */
    private String status;
    
    /**
     * 订单创建时间
     * 格式：yyyy-MM-dd HH:mm:ss
     */
    private LocalDateTime createTime;
}

