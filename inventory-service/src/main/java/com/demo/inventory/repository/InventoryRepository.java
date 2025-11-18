package com.demo.inventory.repository;

import com.demo.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * 库存数据访问层接口
 * 
 * 功能说明：
 * 提供库存数据的CRUD操作和自定义查询方法
 * 
 * 技术说明：
 * 1. 继承JpaRepository，自动获得基础的CRUD方法：
 *    - save(): 保存/更新库存
 *    - findById(): 根据ID查询库存
 *    - findAll(): 查询所有库存
 *    - deleteById(): 删除库存
 *    - count(): 统计库存记录数量
 * 
 * 2. 使用Spring Data JPA的方法命名规则，自动生成SQL
 * 
 * 3. 配合@Transactional注解实现事务管理
 * 
 * 并发控制：
 * 结合Inventory实体中的version字段，实现乐观锁
 * 更新时JPA会自动检查version是否匹配
 * 
 * @author demo
 * @version 1.0.0
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    
    /**
     * 根据商品ID查询库存信息
     * 
     * SQL等价于: SELECT * FROM t_inventory WHERE product_id = ?
     * 
     * 使用场景：
     * 1. 扣减库存前查询当前库存
     * 2. 前端展示商品库存数量
     * 3. 订单创建时校验库存是否充足
     * 
     * @param productId 商品ID，每个商品对应唯一的库存记录
     * @return Optional<Inventory> 库存对象（可能为空）
     */
    Optional<Inventory> findByProductId(Long productId);
}

