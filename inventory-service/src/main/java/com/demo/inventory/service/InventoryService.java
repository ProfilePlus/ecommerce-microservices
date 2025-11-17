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

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {
    
    private final InventoryRepository inventoryRepository;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;
    
    /**
     * 监听Kafka订单创建消息
     */
    @KafkaListener(topics = "order-created", groupId = "inventory-service-group")
    @Transactional
    public void handleOrderCreated(Map<String, Object> orderData) {
        log.info("收到订单创建消息: {}", orderData);
        
        try {
            Long productId = Long.valueOf(orderData.get("productId").toString());
            Integer quantity = Integer.valueOf(orderData.get("quantity").toString());
            String orderNo = orderData.get("orderNo").toString();
            
            // 扣减库存
            boolean success = deductInventory(productId, quantity);
            
            if (success) {
                log.info("库存扣减成功: productId={}, quantity={}", productId, quantity);
                
                // 发送RabbitMQ通知消息
                Map<String, Object> notification = Map.of(
                    "orderNo", orderNo,
                    "productId", productId,
                    "type", "INVENTORY_DEDUCTED",
                    "message", "库存扣减成功"
                );
                rabbitTemplate.convertAndSend("order.exchange", "notification.order", notification);
                log.info("已发送RabbitMQ通知: {}", orderNo);
            } else {
                log.error("库存不足: productId={}, quantity={}", productId, quantity);
            }
        } catch (Exception e) {
            log.error("处理订单消息失败", e);
        }
    }
    
    /**
     * 扣减库存 (乐观锁)
     */
    @Transactional
    public boolean deductInventory(Long productId, Integer quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        
        if (inventory.getStock() < quantity) {
            return false;
        }
        
        inventory.setStock(inventory.getStock() - quantity);
        inventory.setVersion(inventory.getVersion() + 1);
        inventoryRepository.save(inventory);
        
        // 更新Redis缓存
        String cacheKey = "inventory:" + productId;
        redisTemplate.opsForValue().set(cacheKey, String.valueOf(inventory.getStock()));
        
        return true;
    }
    
    /**
     * 查询库存
     */
    public Integer getStock(Long productId) {
        // 先查Redis
        String cacheKey = "inventory:" + productId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return Integer.valueOf(cached);
        }
        
        // 查数据库
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        
        // 写回Redis
        redisTemplate.opsForValue().set(cacheKey, String.valueOf(inventory.getStock()));
        
        return inventory.getStock();
    }
}

