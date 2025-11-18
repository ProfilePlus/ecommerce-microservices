package com.demo.order.repository;

import com.demo.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * 订单数据访问层接口
 * 
 * 功能说明：
 * 提供订单数据的CRUD操作和自定义查询方法
 * 
 * 技术说明：
 * 1. 继承JpaRepository，自动获得基础的CRUD方法：
 *    - save(): 保存/更新订单
 *    - findById(): 根据ID查询订单
 *    - findAll(): 查询所有订单
 *    - deleteById(): 删除订单
 *    - count(): 统计订单数量
 * 
 * 2. 使用Spring Data JPA的方法命名规则，自动生成SQL：
 *    - findByOrderNo: 根据订单号查询
 *    - findByUserId: 根据用户ID查询
 * 
 * 3. 返回Optional防止空指针异常
 * 
 * @author demo
 * @version 1.0.0
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    /**
     * 根据订单号查询订单
     * 
     * SQL等价于: SELECT * FROM t_order WHERE order_no = ?
     * 
     * @param orderNo 订单号，全局唯一
     * @return Optional<Order> 订单对象（可能为空）
     */
    Optional<Order> findByOrderNo(String orderNo);
    
    /**
     * 根据用户ID查询该用户的所有订单
     * 
     * SQL等价于: SELECT * FROM t_order WHERE user_id = ? ORDER BY create_time DESC
     * 
     * @param userId 用户ID
     * @return List<Order> 订单列表（可能为空列表）
     */
    List<Order> findByUserId(Long userId);
}

