package com.demo.inventory.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 库存实体类
 * 
 * 功能说明：
 * 库存数据模型，存储商品的库存信息和版本控制
 * 
 * 数据库表：t_inventory
 * 
 * 业务说明：
 * 1. productId是业务主键，每个商品对应唯一的库存记录
 * 2. version字段用于实现乐观锁，解决高并发下的库存扣减问题
 * 3. 库存扣减采用"先查询再更新"的方式，通过版本号保证数据一致性
 * 
 * 并发控制：
 * - 使用乐观锁（version字段）防止超卖
 * - 每次更新库存时，version递增
 * - 如果更新时version不匹配，说明数据已被修改，需要重试
 * 
 * @author demo
 * @version 1.0.0
 */
@Data  // Lombok注解：自动生成getter、setter、toString、equals、hashCode方法
@Entity  // JPA注解：标识这是一个实体类，映射到数据库表
@Table(name = "t_inventory")  // 指定映射的数据库表名
public class Inventory {
    
    /**
     * 主键ID
     * 数据库自动生成，使用自增策略
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 商品ID（业务主键）
     * 每个商品对应唯一的库存记录
     * 特性：全局唯一、不可为空
     */
    @Column(unique = true, nullable = false)
    private Long productId;
    
    /**
     * 商品名称
     * 冗余存储，方便日志记录和问题排查
     */
    private String productName;
    
    /**
     * 库存数量
     * 当前可用库存，必须大于等于0
     * 扣减库存时需要先判断是否充足
     */
    private Integer stock;
    
    /**
     * 版本号（乐观锁）
     * 用于并发控制，防止超卖问题
     * 每次更新库存时version自动递增
     * 
     * 工作原理：
     * 1. 查询库存时获取当前version
     * 2. 更新库存时，where条件带上version
     * 3. 如果version不匹配，更新失败，需要重试
     */
    private Integer version;
    
    /**
     * 最后更新时间
     * 记录库存最近一次变更的时间
     */
    private LocalDateTime updateTime;
}

