package com.demo.order.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 订单请求DTO（数据传输对象）
 * 
 * 功能说明：
 * 用于接收前端或客户端提交的订单创建请求
 * 
 * 使用场景：
 * POST /api/orders 接口的请求体
 * 
 * 字段校验：
 * 实际项目中应添加校验注解，如：
 * - @NotNull: 不能为空
 * - @Min: 最小值
 * - @Positive: 必须为正数
 * 
 * @author demo
 * @version 1.0.0
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
public class OrderRequest {
    
    /**
     * 用户ID
     * 标识下单的用户
     */
    private Long userId;
    
    /**
     * 商品ID
     * 标识要购买的商品
     */
    private Long productId;
    
    /**
     * 商品名称
     * 商品的展示名称
     */
    private String productName;
    
    /**
     * 购买数量
     * 必须大于0
     */
    private Integer quantity;
    
    /**
     * 订单总金额
     * 计算公式：商品单价 × 购买数量
     * 使用BigDecimal避免浮点数精度问题
     */
    private BigDecimal totalAmount;
}

