package com.demo.order.entity;

import lombok.Data;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体类
 * 
 * 功能说明：
 * 订单数据模型，存储订单的基本信息和状态
 * 
 * 数据库表：t_order
 * 
 * 业务说明：
 * 1. orderNo是业务主键，全局唯一，用于业务层面的订单标识
 * 2. id是数据库主键，用于数据库层面的记录标识
 * 3. status表示订单状态：PENDING(待处理)、COMPLETED(已完成)、CANCELLED(已取消)等
 * 4. 支持多字段查询：按订单号查询、按用户ID查询
 * 
 * @author demo
 * @version 1.0.0
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
@Entity  // JPA注解：标识这是一个实体类，映射到数据库表
@Table(name = "t_order")  // 指定映射的数据库表名
public class Order {
    
    /**
     * 主键ID
     * 数据库自动生成，使用自增策略
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 订单号（业务主键）
     * 格式：ORD + 时间戳 + 8位随机字符
     * 示例：ORD1700000000000abc12345
     * 特性：全局唯一、不可为空
     */
    @Column(unique = true, nullable = false)
    private String orderNo;
    
    /**
     * 用户ID
     * 关联用户信息，用于查询用户的所有订单
     */
    private Long userId;
    
    /**
     * 商品ID
     * 关联商品信息，用于扣减库存
     */
    private Long productId;
    
    /**
     * 商品名称
     * 冗余存储，避免商品信息变更影响历史订单
     */
    private String productName;
    
    /**
     * 购买数量
     * 必须大于0
     */
    private Integer quantity;
    
    /**
     * 订单总金额
     * 使用BigDecimal避免浮点数精度问题
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
     */
    private LocalDateTime createTime;
    
    /**
     * 订单更新时间
     * 记录订单状态变更的时间
     */
    private LocalDateTime updateTime;
}

