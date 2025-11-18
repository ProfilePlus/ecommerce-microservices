package com.demo.inventory.service;

import com.demo.inventory.entity.Inventory;
import com.demo.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 库存业务服务类
 * 
 * 功能说明：
 * 库存管理的核心实现，包括库存扣减、查询等功能
 * 
 * 技术亮点：
 * 1. 消息驱动：监听Kafka订单消息，自动扣减库存
 * 2. 乐观锁：使用version字段防止超卖问题
 * 3. 缓存策略：使用Redis缓存库存数据，提高查询性能
 * 4. 消息发布：通过RabbitMQ发送通知消息
 * 5. 事务管理：保证库存扣减的原子性
 * 
 * 业务流程：
 * 订单服务发送Kafka消息 -> 库存服务监听消息 -> 扣减库存 -> 更新Redis -> 发送RabbitMQ通知
 * 
 * 并发控制：
 * 使用乐观锁（version字段）防止并发扣减导致的超卖问题
 * 
 * @author demo
 * @version 1.0.0
 */
@Slf4j  // Lombok注解：自动生成日志对象log
@Service  // 标识这是一个服务层组件，由Spring容器管理
@RequiredArgsConstructor  // Lombok注解：自动生成包含final字段的构造函数
public class InventoryService {
    
    /**
     * 库存数据访问层
     * 用于操作库存数据库表
     */
    private final InventoryRepository inventoryRepository;
    
    /**
     * RabbitMQ消息发送模板
     * 用于发送库存扣减成功的通知消息
     */
    private final RabbitTemplate rabbitTemplate;
    
    /**
     * Redis字符串操作模板
     * 用于缓存库存数据，提高查询性能
     */
    private final StringRedisTemplate redisTemplate;
    
    /**
     * 监听Kafka订单创建消息（消息消费者）
     * 
     * 功能说明：
     * 当订单服务创建订单后，会发送消息到Kafka的order-created主题
     * 库存服务监听此主题，接收到消息后自动扣减库存
     * 
     * 配置说明：
     * - topics: 监听的Kafka主题名称
     * - groupId: 消费者组ID，同一组内的消费者共享消息，实现负载均衡
     * 
     * 业务流程：
     * 1. 接收订单创建消息
     * 2. 解析消息内容（商品ID、数量、订单号）
     * 3. 调用库存扣减方法
     * 4. 扣减成功后发送RabbitMQ通知
     * 5. 扣减失败则记录错误日志
     * 
     * 事务说明：
     * @Transactional保证库存扣减的原子性
     * 如果扣减失败，会自动回滚
     * 
     * 异常处理：
     * 捕获所有异常，避免消息消费失败导致服务不可用
     * 实际项目中应该有重试机制和死信队列
     * 
     * @param orderData 订单数据，包含productId、quantity、orderNo等字段
     */
    @KafkaListener(topics = "order-created", groupId = "inventory-service-group")
    @Transactional  // 开启事务管理
    public void handleOrderCreated(Map<String, Object> orderData) {
        log.info("收到订单创建消息: {}", orderData);
        
        try {
            // 1. 解析订单消息中的关键信息
            Long productId = Long.valueOf(orderData.get("productId").toString());
            Integer quantity = Integer.valueOf(orderData.get("quantity").toString());
            String orderNo = orderData.get("orderNo").toString();
            
            // 2. 扣减库存
            boolean success = deductInventory(productId, quantity);
            
            if (success) {
                // 3. 库存扣减成功，记录日志
                log.info("库存扣减成功: productId={}, quantity={}", productId, quantity);
                
                // 4. 发送RabbitMQ通知消息给通知服务
                // 通知服务会发送短信、邮件等通知给用户
                Map<String, Object> notification = Map.of(
                    "orderNo", orderNo,
                    "productId", productId,
                    "type", "INVENTORY_DEDUCTED",  // 通知类型
                    "message", "库存扣减成功"
                );
                
                // 发送到RabbitMQ交换机
                // exchange: order.exchange
                // routingKey: notification.order
                rabbitTemplate.convertAndSend("order.exchange", "notification.order", notification);
                log.info("已发送RabbitMQ通知: {}", orderNo);
            } else {
                // 5. 库存扣减失败（库存不足）
                log.error("库存不足: productId={}, quantity={}", productId, quantity);
                // TODO: 实际项目中应该发送库存不足的通知，并更新订单状态为"已取消"
            }
        } catch (Exception e) {
            // 6. 处理异常
            log.error("处理订单消息失败", e);
            // TODO: 实际项目中应该有重试机制或将消息放入死信队列
        }
    }
    
    /**
     * 扣减库存（使用乐观锁）
     * 
     * 功能说明：
     * 扣减指定商品的库存数量
     * 
     * 并发控制：
     * 使用乐观锁（version字段）防止并发扣减导致的超卖问题
     * 
     * 工作原理：
     * 1. 查询库存时获取当前version
     * 2. 更新库存时，version自动递增
     * 3. JPA会在更新时检查version是否匹配
     * 4. 如果version不匹配，说明数据已被其他事务修改，更新失败
     * 
     * 业务流程：
     * 1. 查询商品库存
     * 2. 校验库存是否充足
     * 3. 扣减库存并更新version
     * 4. 保存到数据库
     * 5. 更新Redis缓存
     * 
     * 事务说明：
     * @Transactional保证扣减操作的原子性
     * 
     * @param productId 商品ID
     * @param quantity 扣减数量
     * @return boolean true-扣减成功，false-库存不足
     * @throws RuntimeException 商品不存在时抛出
     */
    @Transactional  // 开启事务管理
    public boolean deductInventory(Long productId, Integer quantity) {
        // 1. 查询商品库存
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        
        // 2. 校验库存是否充足
        if (inventory.getStock() < quantity) {
            return false;  // 库存不足，返回失败
        }
        
        // 3. 扣减库存
        inventory.setStock(inventory.getStock() - quantity);
        
        // 4. 更新版本号（乐观锁）
        // JPA会自动在SQL中添加 WHERE version = ? 条件
        inventory.setVersion(inventory.getVersion() + 1);
        
        // 5. 保存到数据库
        // 如果version不匹配，会抛出OptimisticLockException异常
        inventoryRepository.save(inventory);
        
        // 6. 更新Redis缓存
        // 保持缓存和数据库的数据一致性
        String cacheKey = "inventory:" + productId;
        redisTemplate.opsForValue().set(cacheKey, String.valueOf(inventory.getStock()));
        
        return true;  // 扣减成功
    }
    
    /**
     * 查询库存
     * 
     * 功能说明：
     * 查询指定商品的库存数量
     * 
     * 查询策略（缓存穿透预防）：
     * 1. 先从Redis缓存查询（快速，毫秒级）
     * 2. 缓存命中则直接返回
     * 3. 缓存未命中则从数据库查询
     * 4. 查询结果写回Redis缓存
     * 
     * 性能优化：
     * - 使用Redis缓存，减少数据库压力
     * - 适用于高频查询的场景（如商品详情页）
     * 
     * 注意事项：
     * - 缓存可能存在短暂延迟
     * - 扣减库存时会同步更新缓存
     * 
     * @param productId 商品ID
     * @return Integer 库存数量
     * @throws RuntimeException 商品不存在时抛出
     */
    public Integer getStock(Long productId) {
        // 1. 先从Redis查询（一级缓存）
        String cacheKey = "inventory:" + productId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            // 缓存命中，直接返回
            return Integer.valueOf(cached);
        }
        
        // 2. Redis中没有，从数据库查询
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        
        // 3. 写回Redis（缓存预热）
        redisTemplate.opsForValue().set(cacheKey, String.valueOf(inventory.getStock()));
        
        return inventory.getStock();
    }
}

