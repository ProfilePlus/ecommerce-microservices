# Java代码能力提升指南
## 从初级到中级的优雅代码之路

---

## 📋 文档说明

**适用人群**：
- 已完成基础版和升级版的开发者
- 希望提升代码质量的初中级工程师
- 想让代码更优雅、更易维护的开发者

**学习目标**：
- ✅ 掌握优雅编码技巧
- ✅ 学会常用设计模式
- ✅ 理解代码重构方法
- ✅ 提升代码可读性和可维护性
- ✅ 达到中级工程师代码水平

**预期效果**：
- 代码行数减少 30%+
- Bug 率降低 50%+
- 可读性提升 80%+
- 面试加分 40%+

---

## 第一章：代码坏味道与重构

### 1.1 识别代码坏味道

#### 坏味道1：过长的方法

**问题代码**：

```java
@Service
public class OrderService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private InventoryService inventoryService;
    
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    // 方法过长（100+ 行）
    public OrderResponse createOrder(OrderRequest request) {
        // 1. 参数校验
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (request.getProductId() == null) {
            throw new IllegalArgumentException("商品ID不能为空");
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("购买数量必须大于0");
        }
        
        // 2. 检查库存
        String inventoryKey = "inventory:" + request.getProductId();
        Integer stock = (Integer) redisTemplate.opsForValue().get(inventoryKey);
        if (stock == null) {
            // 从数据库查询
            Inventory inventory = inventoryService.getByProductId(request.getProductId());
            if (inventory == null) {
                throw new RuntimeException("商品不存在");
            }
            stock = inventory.getStock();
            redisTemplate.opsForValue().set(inventoryKey, stock, 10, TimeUnit.MINUTES);
        }
        if (stock < request.getQuantity()) {
            throw new RuntimeException("库存不足");
        }
        
        // 3. 扣减库存
        boolean success = inventoryService.deductStock(request.getProductId(), request.getQuantity());
        if (!success) {
            throw new RuntimeException("扣减库存失败");
        }
        
        // 4. 创建订单
        Order order = new Order();
        order.setOrderNo("ORD" + System.currentTimeMillis() + new Random().nextInt(1000));
        order.setUserId(request.getUserId());
        order.setProductId(request.getProductId());
        order.setProductName(request.getProductName());
        order.setQuantity(request.getQuantity());
        order.setTotalAmount(request.getTotalAmount());
        order.setStatus("PENDING");
        order.setCreateTime(new Date());
        order.setUpdateTime(new Date());
        
        orderRepository.save(order);
        
        // 5. 发送消息
        Map<String, Object> message = new HashMap<>();
        message.put("orderId", order.getId());
        message.put("orderNo", order.getOrderNo());
        message.put("userId", order.getUserId());
        message.put("productId", order.getProductId());
        message.put("quantity", order.getQuantity());
        message.put("totalAmount", order.getTotalAmount());
        message.put("createTime", order.getCreateTime());
        
        kafkaTemplate.send("order-created", message);
        
        // 6. 构建响应
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setStatus(order.getStatus());
        response.setMessage("订单创建成功");
        
        return response;
    }
}
```

**优雅代码**：

```java
@Service
@Slf4j
public class OrderService {
    
    @Autowired
    private OrderValidator orderValidator;
    
    @Autowired
    private InventoryChecker inventoryChecker;
    
    @Autowired
    private OrderFactory orderFactory;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private OrderEventPublisher eventPublisher;
    
    /**
     * 创建订单（主流程）
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createOrder(OrderRequest request) {
        // 1. 参数校验
        orderValidator.validate(request);
        
        // 2. 检查并扣减库存
        inventoryChecker.checkAndDeduct(request.getProductId(), request.getQuantity());
        
        // 3. 创建订单
        Order order = orderFactory.create(request);
        orderRepository.save(order);
        
        // 4. 发布事件
        eventPublisher.publishOrderCreated(order);
        
        // 5. 返回结果
        return OrderResponse.success(order);
    }
}
```

**改进点**：
- ✅ 方法从 100+ 行减少到 20 行
- ✅ 职责单一，易于理解
- ✅ 每个步骤都有明确的类负责
- ✅ 易于测试和维护

---

#### 坏味道2：重复代码

**问题代码**：

```java
// 订单服务中
public Order getOrderById(Long orderId) {
    if (orderId == null) {
        throw new IllegalArgumentException("订单ID不能为空");
    }
    
    String cacheKey = "order:" + orderId;
    Order order = (Order) redisTemplate.opsForValue().get(cacheKey);
    
    if (order == null) {
        order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("订单不存在"));
        redisTemplate.opsForValue().set(cacheKey, order, 10, TimeUnit.MINUTES);
    }
    
    return order;
}

// 库存服务中
public Inventory getInventoryById(Long inventoryId) {
    if (inventoryId == null) {
        throw new IllegalArgumentException("库存ID不能为空");
    }
    
    String cacheKey = "inventory:" + inventoryId;
    Inventory inventory = (Inventory) redisTemplate.opsForValue().get(cacheKey);
    
    if (inventory == null) {
        inventory = inventoryRepository.findById(inventoryId)
            .orElseThrow(() -> new RuntimeException("库存不存在"));
        redisTemplate.opsForValue().set(cacheKey, inventory, 10, TimeUnit.MINUTES);
    }
    
    return inventory;
}
```

**优雅代码**：

```java
/**
 * 通用缓存服务（抽取公共逻辑）
 */
@Component
@Slf4j
public class CacheService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 查询缓存，缓存不存在则从数据库加载
     *
     * @param key 缓存键
     * @param loader 数据加载器
     * @param expire 过期时间（秒）
     * @return 数据对象
     */
    public <T> T getOrLoad(String key, Supplier<T> loader, long expire) {
        // 1. 尝试从缓存获取
        T value = (T) redisTemplate.opsForValue().get(key);
        
        if (value != null) {
            log.debug("缓存命中: {}", key);
            return value;
        }
        
        // 2. 缓存未命中，从数据库加载
        log.debug("缓存未命中，从数据库加载: {}", key);
        value = loader.get();
        
        if (value == null) {
            throw new ResourceNotFoundException("数据不存在");
        }
        
        // 3. 写入缓存
        redisTemplate.opsForValue().set(key, value, expire, TimeUnit.SECONDS);
        
        return value;
    }
}

/**
 * 订单服务（使用通用缓存）
 */
@Service
public class OrderService {
    
    @Autowired
    private CacheService cacheService;
    
    @Autowired
    private OrderRepository orderRepository;
    
    private static final String CACHE_PREFIX = "order:";
    private static final long CACHE_EXPIRE = 600; // 10分钟
    
    public Order getOrderById(Long orderId) {
        Assert.notNull(orderId, "订单ID不能为空");
        
        String cacheKey = CACHE_PREFIX + orderId;
        
        return cacheService.getOrLoad(
            cacheKey,
            () -> orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("订单不存在: " + orderId)),
            CACHE_EXPIRE
        );
    }
}
```

**改进点**：
- ✅ 消除重复代码
- ✅ 提取通用逻辑
- ✅ 符合 DRY 原则（Don't Repeat Yourself）
- ✅ 易于维护和扩展

---

#### 坏味道3：过多的条件判断

**问题代码**：

```java
public void processOrder(Order order) {
    if (order.getStatus().equals("PENDING")) {
        // 待支付逻辑
        sendPaymentNotification(order);
        updateOrderStatus(order, "WAITING_PAYMENT");
    } else if (order.getStatus().equals("PAID")) {
        // 已支付逻辑
        allocateInventory(order);
        updateOrderStatus(order, "PROCESSING");
    } else if (order.getStatus().equals("PROCESSING")) {
        // 处理中逻辑
        shipOrder(order);
        updateOrderStatus(order, "SHIPPED");
    } else if (order.getStatus().equals("SHIPPED")) {
        // 已发货逻辑
        notifyCustomer(order);
        updateOrderStatus(order, "COMPLETED");
    } else if (order.getStatus().equals("CANCELLED")) {
        // 已取消逻辑
        refundPayment(order);
        releaseInventory(order);
    } else {
        throw new IllegalStateException("未知的订单状态: " + order.getStatus());
    }
}
```

**优雅代码（策略模式）**：

```java
/**
 * 订单状态处理器接口
 */
public interface OrderStateHandler {
    /**
     * 处理订单
     */
    void handle(Order order);
    
    /**
     * 是否支持该状态
     */
    boolean support(OrderStatus status);
}

/**
 * 待支付状态处理器
 */
@Component
public class PendingStateHandler implements OrderStateHandler {
    
    @Autowired
    private NotificationService notificationService;
    
    @Override
    public void handle(Order order) {
        // 发送支付通知
        notificationService.sendPaymentNotification(order);
        
        // 更新状态
        order.setStatus(OrderStatus.WAITING_PAYMENT);
    }
    
    @Override
    public boolean support(OrderStatus status) {
        return status == OrderStatus.PENDING;
    }
}

/**
 * 已支付状态处理器
 */
@Component
public class PaidStateHandler implements OrderStateHandler {
    
    @Autowired
    private InventoryService inventoryService;
    
    @Override
    public void handle(Order order) {
        // 分配库存
        inventoryService.allocate(order);
        
        // 更新状态
        order.setStatus(OrderStatus.PROCESSING);
    }
    
    @Override
    public boolean support(OrderStatus status) {
        return status == OrderStatus.PAID;
    }
}

/**
 * 订单状态处理器工厂
 */
@Component
public class OrderStateHandlerFactory {
    
    @Autowired
    private List<OrderStateHandler> handlers;
    
    /**
     * 根据状态获取处理器
     */
    public OrderStateHandler getHandler(OrderStatus status) {
        return handlers.stream()
            .filter(handler -> handler.support(status))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("不支持的订单状态: " + status));
    }
}

/**
 * 订单服务（使用策略模式）
 */
@Service
public class OrderService {
    
    @Autowired
    private OrderStateHandlerFactory handlerFactory;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Transactional(rollbackFor = Exception.class)
    public void processOrder(Order order) {
        // 获取对应的处理器
        OrderStateHandler handler = handlerFactory.getHandler(order.getStatus());
        
        // 执行处理
        handler.handle(order);
        
        // 保存订单
        orderRepository.save(order);
    }
}
```

**改进点**：
- ✅ 消除冗长的 if-else
- ✅ 符合开闭原则（新增状态不需要修改原代码）
- ✅ 职责单一，每个处理器只处理一种状态
- ✅ 易于测试和维护

---

### 1.2 常见重构技巧

#### 技巧1：提取方法

**重构前**：

```java
public void generateOrderReport(Long orderId) {
    Order order = orderRepository.findById(orderId).orElseThrow();
    
    // 计算总金额
    BigDecimal totalAmount = BigDecimal.ZERO;
    for (OrderItem item : order.getItems()) {
        BigDecimal itemAmount = item.getPrice().multiply(new BigDecimal(item.getQuantity()));
        totalAmount = totalAmount.add(itemAmount);
    }
    
    // 计算折扣
    BigDecimal discount = BigDecimal.ZERO;
    if (order.getCouponId() != null) {
        Coupon coupon = couponRepository.findById(order.getCouponId()).orElse(null);
        if (coupon != null && coupon.isValid()) {
            if (coupon.getType().equals("PERCENT")) {
                discount = totalAmount.multiply(coupon.getValue()).divide(new BigDecimal(100));
            } else {
                discount = coupon.getValue();
            }
        }
    }
    
    // 生成报表
    Report report = new Report();
    report.setOrderNo(order.getOrderNo());
    report.setTotalAmount(totalAmount);
    report.setDiscount(discount);
    report.setFinalAmount(totalAmount.subtract(discount));
}
```

**重构后**：

```java
public void generateOrderReport(Long orderId) {
    Order order = findOrderById(orderId);
    
    BigDecimal totalAmount = calculateTotalAmount(order);
    BigDecimal discount = calculateDiscount(order, totalAmount);
    BigDecimal finalAmount = totalAmount.subtract(discount);
    
    Report report = buildReport(order, totalAmount, discount, finalAmount);
}

private Order findOrderById(Long orderId) {
    return orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException("订单不存在: " + orderId));
}

private BigDecimal calculateTotalAmount(Order order) {
    return order.getItems().stream()
        .map(item -> item.getPrice().multiply(new BigDecimal(item.getQuantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
}

private BigDecimal calculateDiscount(Order order, BigDecimal totalAmount) {
    if (order.getCouponId() == null) {
        return BigDecimal.ZERO;
    }
    
    return couponRepository.findById(order.getCouponId())
        .filter(Coupon::isValid)
        .map(coupon -> calculateCouponDiscount(coupon, totalAmount))
        .orElse(BigDecimal.ZERO);
}

private BigDecimal calculateCouponDiscount(Coupon coupon, BigDecimal totalAmount) {
    if ("PERCENT".equals(coupon.getType())) {
        return totalAmount.multiply(coupon.getValue()).divide(new BigDecimal(100));
    }
    return coupon.getValue();
}

private Report buildReport(Order order, BigDecimal totalAmount, 
                          BigDecimal discount, BigDecimal finalAmount) {
    return Report.builder()
        .orderNo(order.getOrderNo())
        .totalAmount(totalAmount)
        .discount(discount)
        .finalAmount(finalAmount)
        .build();
}
```

---

#### 技巧2：引入解释性变量

**重构前**：

```java
if ((order.getStatus().equals("PAID") || order.getStatus().equals("PROCESSING")) 
    && order.getCreateTime().before(new Date(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000))
    && order.getTotalAmount().compareTo(new BigDecimal("1000")) > 0) {
    // 处理逻辑
}
```

**重构后**：

```java
boolean isPaidOrProcessing = order.getStatus().equals("PAID") 
                           || order.getStatus().equals("PROCESSING");
boolean isOlderThan7Days = order.getCreateTime().before(getDateBefore(7));
boolean isHighValue = order.getTotalAmount().compareTo(new BigDecimal("1000")) > 0;

if (isPaidOrProcessing && isOlderThan7Days && isHighValue) {
    // 处理逻辑
}
```

---

#### 技巧3：以对象取代数据值

**重构前**：

```java
@Entity
public class Order {
    private Long id;
    private String orderNo;
    private Long userId;
    private String userPhone;  // 用户电话
    private String userEmail;  // 用户邮箱
    private String userAddress; // 用户地址
    // ...
}
```

**重构后**：

```java
@Entity
public class Order {
    private Long id;
    private String orderNo;
    
    @Embedded
    private UserInfo userInfo;  // 封装用户信息
    
    // ...
}

@Embeddable
public class UserInfo {
    private Long userId;
    private String phone;
    private String email;
    private String address;
    
    // 业务方法
    public boolean isValidPhone() {
        return phone != null && phone.matches("^1[3-9]\\d{9}$");
    }
    
    public boolean isValidEmail() {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }
}
```

---

## 第二章：优雅的异常处理

### 2.1 统一异常处理

**问题代码**：

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest request) {
        try {
            OrderResponse response = orderService.createOrder(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                OrderResponse.error("参数错误: " + e.getMessage())
            );
        } catch (RuntimeException e) {
            return ResponseEntity.status(500).body(
                OrderResponse.error("系统错误: " + e.getMessage())
            );
        }
    }
    
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long orderId) {
        try {
            Order order = orderService.getOrderById(orderId);
            return ResponseEntity.ok(OrderResponse.success(order));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                OrderResponse.error("参数错误: " + e.getMessage())
            );
        } catch (RuntimeException e) {
            return ResponseEntity.status(500).body(
                OrderResponse.error("系统错误: " + e.getMessage())
            );
        }
    }
}
```

**优雅代码**：

**1. 定义业务异常**：

```java
/**
 * 业务异常基类
 */
public class BusinessException extends RuntimeException {
    
    private final ErrorCode errorCode;
    
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

/**
 * 订单异常
 */
public class OrderNotFoundException extends BusinessException {
    public OrderNotFoundException(String message) {
        super(ErrorCode.ORDER_NOT_FOUND, message);
    }
}

/**
 * 库存异常
 */
public class InsufficientStockException extends BusinessException {
    public InsufficientStockException(String message) {
        super(ErrorCode.INSUFFICIENT_STOCK, message);
    }
}
```

**2. 定义错误码**：

```java
/**
 * 错误码枚举
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {
    
    // 成功
    SUCCESS(200, "操作成功"),
    
    // 客户端错误 (4xx)
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    
    // 业务错误 (5xx)
    ORDER_NOT_FOUND(5001, "订单不存在"),
    INSUFFICIENT_STOCK(5002, "库存不足"),
    DUPLICATE_ORDER(5003, "订单重复"),
    PAYMENT_FAILED(5004, "支付失败"),
    
    // 系统错误 (9xxx)
    SYSTEM_ERROR(9999, "系统错误"),
    DATABASE_ERROR(9001, "数据库错误"),
    REDIS_ERROR(9002, "缓存错误"),
    MQ_ERROR(9003, "消息队列错误");
    
    private final int code;
    private final String message;
}
```

**3. 全局异常处理器**：

```java
/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        
        ApiResponse<Void> response = ApiResponse.error(
            e.getErrorCode().getCode(),
            e.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * 参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException e) {
        
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        
        log.warn("参数校验失败: {}", message);
        
        ApiResponse<Void> response = ApiResponse.error(
            ErrorCode.PARAM_ERROR.getCode(),
            message
        );
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 系统异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("系统异常", e);
        
        ApiResponse<Void> response = ApiResponse.error(
            ErrorCode.SYSTEM_ERROR.getCode(),
            "系统繁忙，请稍后重试"
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
```

**4. 统一响应格式**：

```java
/**
 * 统一 API 响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    
    /**
     * 响应码
     */
    private Integer code;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 响应数据
     */
    private T data;
    
    /**
     * 时间戳
     */
    private Long timestamp;
    
    /**
     * 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .code(ErrorCode.SUCCESS.getCode())
            .message(ErrorCode.SUCCESS.getMessage())
            .data(data)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /**
     * 成功响应（无数据）
     */
    public static <T> ApiResponse<T> success() {
        return success(null);
    }
    
    /**
     * 错误响应
     */
    public static <T> ApiResponse<T> error(Integer code, String message) {
        return ApiResponse.<T>builder()
            .code(code)
            .message(message)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /**
     * 错误响应（使用错误码）
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return error(errorCode.getCode(), errorCode.getMessage());
    }
}
```

**5. 简化后的 Controller**：

```java
/**
 * 订单控制器（简洁版）
 */
@RestController
@RequestMapping("/api/orders")
@Slf4j
public class OrderController {
    
    @Autowired
    private OrderService orderService;
    
    /**
     * 创建订单
     */
    @PostMapping
    public ApiResponse<OrderResponse> createOrder(
            @Valid @RequestBody OrderRequest request) {
        
        log.info("创建订单: {}", request);
        
        OrderResponse response = orderService.createOrder(request);
        
        return ApiResponse.success(response);
    }
    
    /**
     * 查询订单
     */
    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable Long orderId) {
        
        log.info("查询订单: orderId={}", orderId);
        
        Order order = orderService.getOrderById(orderId);
        
        return ApiResponse.success(OrderResponse.from(order));
    }
}
```

**改进点**：
- ✅ 消除重复的 try-catch
- ✅ 统一异常处理
- ✅ 统一响应格式
- ✅ 代码更简洁优雅

---

### 2.2 参数校验优化

**问题代码**：

```java
@PostMapping
public ApiResponse<OrderResponse> createOrder(@RequestBody OrderRequest request) {
    // 手动校验
    if (request.getUserId() == null) {
        throw new BusinessException(ErrorCode.PARAM_ERROR, "用户ID不能为空");
    }
    if (request.getProductId() == null) {
        throw new BusinessException(ErrorCode.PARAM_ERROR, "商品ID不能为空");
    }
    if (request.getQuantity() == null || request.getQuantity() <= 0) {
        throw new BusinessException(ErrorCode.PARAM_ERROR, "购买数量必须大于0");
    }
    if (request.getTotalAmount() == null || request.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
        throw new BusinessException(ErrorCode.PARAM_ERROR, "订单金额必须大于0");
    }
    
    // 业务逻辑
    return ApiResponse.success(orderService.createOrder(request));
}
```

**优雅代码**：

```java
/**
 * 订单请求 DTO（使用 JSR-303 校验）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    
    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    @Positive(message = "用户ID必须大于0")
    private Long userId;
    
    /**
     * 商品ID
     */
    @NotNull(message = "商品ID不能为空")
    @Positive(message = "商品ID必须大于0")
    private Long productId;
    
    /**
     * 商品名称
     */
    @NotBlank(message = "商品名称不能为空")
    @Length(max = 100, message = "商品名称长度不能超过100")
    private String productName;
    
    /**
     * 购买数量
     */
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为1")
    @Max(value = 999, message = "购买数量不能超过999")
    private Integer quantity;
    
    /**
     * 订单金额
     */
    @NotNull(message = "订单金额不能为空")
    @DecimalMin(value = "0.01", message = "订单金额必须大于0")
    @DecimalMax(value = "999999.99", message = "订单金额不能超过999999.99")
    @Digits(integer = 6, fraction = 2, message = "订单金额格式不正确")
    private BigDecimal totalAmount;
    
    /**
     * 收货地址
     */
    @NotBlank(message = "收货地址不能为空")
    @Length(max = 200, message = "收货地址长度不能超过200")
    private String address;
    
    /**
     * 手机号
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}

/**
 * 订单控制器（使用 @Valid 自动校验）
 */
@PostMapping
public ApiResponse<OrderResponse> createOrder(
        @Valid @RequestBody OrderRequest request) {
    
    // 参数已自动校验，直接执行业务逻辑
    OrderResponse response = orderService.createOrder(request);
    
    return ApiResponse.success(response);
}
```

**自定义校验注解**：

```java
/**
 * 订单金额校验注解
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OrderAmountValidator.class)
@Documented
public @interface ValidOrderAmount {
    
    String message() default "订单金额不合法";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}

/**
 * 订单金额校验器
 */
public class OrderAmountValidator implements ConstraintValidator<ValidOrderAmount, BigDecimal> {
    
    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        
        // 金额必须大于0且小于100万
        return value.compareTo(BigDecimal.ZERO) > 0 
            && value.compareTo(new BigDecimal("1000000")) < 0;
    }
}

// 使用
@ValidOrderAmount(message = "订单金额必须在0-100万之间")
private BigDecimal totalAmount;
```

---

## 第三章：优雅的日志处理

### 3.1 日志最佳实践

**问题代码**：

```java
@Service
public class OrderService {
    
    public Order createOrder(OrderRequest request) {
        System.out.println("开始创建订单: " + request);
        
        Order order = new Order();
        order.setOrderNo("ORD" + System.currentTimeMillis());
        
        System.out.println("订单号: " + order.getOrderNo());
        
        try {
            orderRepository.save(order);
            System.out.println("订单保存成功");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("订单保存失败");
        }
        
        return order;
    }
}
```

**优雅代码**：

```java
@Service
@Slf4j  // Lombok 注解，自动生成 log 对象
public class OrderService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private OrderFactory orderFactory;
    
    /**
     * 创建订单
     */
    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(OrderRequest request) {
        log.info("创建订单开始, userId={}, productId={}, quantity={}", 
                 request.getUserId(), request.getProductId(), request.getQuantity());
        
        // 生成订单
        Order order = orderFactory.create(request);
        log.debug("生成订单号: {}", order.getOrderNo());
        
        // 保存订单
        try {
            orderRepository.save(order);
            log.info("订单创建成功, orderNo={}, orderId={}", order.getOrderNo(), order.getId());
        } catch (DataAccessException e) {
            log.error("订单保存失败, orderNo={}, error={}", order.getOrderNo(), e.getMessage(), e);
            throw new DatabaseException("订单保存失败", e);
        }
        
        return order;
    }
}
```

**日志级别使用规范**：

```java
// TRACE: 最详细的信息，一般不使用
log.trace("进入方法, params={}", params);

// DEBUG: 调试信息，开发环境使用
log.debug("查询数据库, sql={}", sql);

// INFO: 重要的业务流程
log.info("用户登录成功, userId={}, ip={}", userId, ip);

// WARN: 警告信息（不影响运行，但需要关注）
log.warn("库存不足, productId={}, stock={}, required={}", productId, stock, required);

// ERROR: 错误信息（影响业务流程）
log.error("支付失败, orderNo={}, error={}", orderNo, e.getMessage(), e);
```

### 3.2 日志切面（AOP）

```java
/**
 * 日志切面（自动记录接口日志）
 */
@Aspect
@Component
@Slf4j
public class LogAspect {
    
    /**
     * 切入点：所有 Controller 的公共方法
     */
    @Pointcut("execution(public * com.demo.*.controller..*.*(..))")
    public void controllerLog() {}
    
    /**
     * 环绕通知：记录请求日志
     */
    @Around("controllerLog()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        // 获取请求信息
        ServletRequestAttributes attributes = 
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        
        String method = request.getMethod();
        String url = request.getRequestURI();
        String ip = getIpAddress(request);
        String className = point.getSignature().getDeclaringTypeName();
        String methodName = point.getSignature().getName();
        Object[] args = point.getArgs();
        
        // 记录请求日志
        log.info("请求开始 => method={}, url={}, ip={}, class={}, method={}, args={}", 
                 method, url, ip, className, methodName, 
                 JSON.toJSONString(args));
        
        Object result;
        try {
            // 执行方法
            result = point.proceed();
            
            long duration = System.currentTimeMillis() - startTime;
            
            // 记录响应日志
            log.info("请求结束 => method={}, url={}, duration={}ms, result={}", 
                     method, url, duration, JSON.toJSONString(result));
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            
            // 记录异常日志
            log.error("请求异常 => method={}, url={}, duration={}ms, error={}", 
                      method, url, duration, e.getMessage(), e);
            
            throw e;
        }
        
        return result;
    }
    
    /**
     * 获取真实 IP
     */
    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
```

---

## 第四章：设计模式实战

### 4.1 工厂模式

**使用场景**：创建复杂对象

```java
/**
 * 订单工厂
 */
@Component
public class OrderFactory {
    
    @Autowired
    private SnowflakeIdGenerator idGenerator;
    
    /**
     * 创建订单
     */
    public Order create(OrderRequest request) {
        return Order.builder()
            .orderNo(generateOrderNo())
            .userId(request.getUserId())
            .productId(request.getProductId())
            .productName(request.getProductName())
            .quantity(request.getQuantity())
            .totalAmount(request.getTotalAmount())
            .status(OrderStatus.PENDING)
            .createTime(LocalDateTime.now())
            .updateTime(LocalDateTime.now())
            .build();
    }
    
    /**
     * 生成订单号
     */
    private String generateOrderNo() {
        // ORD + yyyyMMdd + 雪花ID
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        long id = idGenerator.nextId();
        return String.format("ORD%s%d", date, id);
    }
}
```

### 4.2 策略模式

**使用场景**：支付方式、优惠计算

```java
/**
 * 支付策略接口
 */
public interface PaymentStrategy {
    /**
     * 支付
     */
    PaymentResult pay(PaymentRequest request);
    
    /**
     * 是否支持该支付方式
     */
    boolean support(PaymentType type);
}

/**
 * 支付宝支付
 */
@Component
@Slf4j
public class AlipayStrategy implements PaymentStrategy {
    
    @Override
    public PaymentResult pay(PaymentRequest request) {
        log.info("使用支付宝支付, amount={}", request.getAmount());
        
        // 调用支付宝 API
        // ...
        
        return PaymentResult.success("支付成功");
    }
    
    @Override
    public boolean support(PaymentType type) {
        return type == PaymentType.ALIPAY;
    }
}

/**
 * 微信支付
 */
@Component
@Slf4j
public class WechatPayStrategy implements PaymentStrategy {
    
    @Override
    public PaymentResult pay(PaymentRequest request) {
        log.info("使用微信支付, amount={}", request.getAmount());
        
        // 调用微信支付 API
        // ...
        
        return PaymentResult.success("支付成功");
    }
    
    @Override
    public boolean support(PaymentType type) {
        return type == PaymentType.WECHAT;
    }
}

/**
 * 支付服务（使用策略模式）
 */
@Service
public class PaymentService {
    
    @Autowired
    private List<PaymentStrategy> strategies;
    
    /**
     * 支付
     */
    public PaymentResult pay(PaymentRequest request) {
        PaymentStrategy strategy = strategies.stream()
            .filter(s -> s.support(request.getPaymentType()))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "不支持的支付方式"));
        
        return strategy.pay(request);
    }
}
```

### 4.3 模板方法模式

**使用场景**：通用流程，部分步骤可定制

```java
/**
 * 订单处理模板
 */
@Slf4j
public abstract class OrderProcessTemplate {
    
    /**
     * 处理订单（模板方法）
     */
    public final OrderResult process(Order order) {
        log.info("开始处理订单: {}", order.getOrderNo());
        
        // 1. 前置检查
        if (!preCheck(order)) {
            return OrderResult.fail("前置检查失败");
        }
        
        // 2. 执行业务（子类实现）
        boolean success = doProcess(order);
        
        if (!success) {
            return OrderResult.fail("业务处理失败");
        }
        
        // 3. 后置处理
        postProcess(order);
        
        log.info("订单处理完成: {}", order.getOrderNo());
        
        return OrderResult.success();
    }
    
    /**
     * 前置检查（可覆盖）
     */
    protected boolean preCheck(Order order) {
        return order != null && order.getOrderNo() != null;
    }
    
    /**
     * 执行业务（必须实现）
     */
    protected abstract boolean doProcess(Order order);
    
    /**
     * 后置处理（可覆盖）
     */
    protected void postProcess(Order order) {
        log.debug("订单后置处理: {}", order.getOrderNo());
    }
}

/**
 * 订单支付处理
 */
@Component
public class OrderPaymentProcessor extends OrderProcessTemplate {
    
    @Autowired
    private PaymentService paymentService;
    
    @Override
    protected boolean doProcess(Order order) {
        // 执行支付逻辑
        PaymentResult result = paymentService.pay(order);
        
        if (result.isSuccess()) {
            order.setStatus(OrderStatus.PAID);
            return true;
        }
        
        return false;
    }
}
```

### 4.4 责任链模式

**使用场景**：订单校验、过滤器

```java
/**
 * 订单校验器接口
 */
public interface OrderValidator {
    /**
     * 校验
     */
    void validate(OrderRequest request);
    
    /**
     * 设置下一个校验器
     */
    void setNext(OrderValidator next);
}

/**
 * 抽象校验器
 */
public abstract class AbstractOrderValidator implements OrderValidator {
    
    protected OrderValidator next;
    
    @Override
    public void setNext(OrderValidator next) {
        this.next = next;
    }
    
    @Override
    public void validate(OrderRequest request) {
        // 执行当前校验
        doValidate(request);
        
        // 执行下一个校验
        if (next != null) {
            next.validate(request);
        }
    }
    
    /**
     * 具体校验逻辑
     */
    protected abstract void doValidate(OrderRequest request);
}

/**
 * 用户校验器
 */
@Component
public class UserValidator extends AbstractOrderValidator {
    
    @Autowired
    private UserService userService;
    
    @Override
    protected void doValidate(OrderRequest request) {
        User user = userService.getById(request.getUserId());
        
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
        
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.USER_INACTIVE, "用户已禁用");
        }
    }
}

/**
 * 商品校验器
 */
@Component
public class ProductValidator extends AbstractOrderValidator {
    
    @Autowired
    private ProductService productService;
    
    @Override
    protected void doValidate(OrderRequest request) {
        Product product = productService.getById(request.getProductId());
        
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "商品不存在");
        }
        
        if (!product.isOnSale()) {
            throw new BusinessException(ErrorCode.PRODUCT_OFF_SALE, "商品已下架");
        }
    }
}

/**
 * 库存校验器
 */
@Component
public class StockValidator extends AbstractOrderValidator {
    
    @Autowired
    private InventoryService inventoryService;
    
    @Override
    protected void doValidate(OrderRequest request) {
        int stock = inventoryService.getStock(request.getProductId());
        
        if (stock < request.getQuantity()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, 
                                      String.format("库存不足，当前库存：%d", stock));
        }
    }
}

/**
 * 校验器链配置
 */
@Configuration
public class ValidatorChainConfig {
    
    @Bean
    public OrderValidator orderValidatorChain(
            UserValidator userValidator,
            ProductValidator productValidator,
            StockValidator stockValidator) {
        
        // 构建责任链
        userValidator.setNext(productValidator);
        productValidator.setNext(stockValidator);
        
        return userValidator;
    }
}

/**
 * 使用责任链
 */
@Service
public class OrderService {
    
    @Autowired
    @Qualifier("orderValidatorChain")
    private OrderValidator validatorChain;
    
    public Order createOrder(OrderRequest request) {
        // 执行责任链校验
        validatorChain.validate(request);
        
        // 创建订单
        // ...
    }
}
```

---

## 第五章：Stream API 优雅编程

### 5.1 集合处理

**问题代码**：

```java
// 查询订单列表并转换
public List<OrderVO> getOrderList(Long userId) {
    List<Order> orders = orderRepository.findByUserId(userId);
    
    List<OrderVO> result = new ArrayList<>();
    for (Order order : orders) {
        if (order.getTotalAmount().compareTo(new BigDecimal("100")) > 0) {
            OrderVO vo = new OrderVO();
            vo.setOrderNo(order.getOrderNo());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setStatus(order.getStatus());
            result.add(vo);
        }
    }
    
    // 按金额排序
    Collections.sort(result, new Comparator<OrderVO>() {
        @Override
        public int compare(OrderVO o1, OrderVO o2) {
            return o2.getTotalAmount().compareTo(o1.getTotalAmount());
        }
    });
    
    return result;
}
```

**优雅代码**：

```java
public List<OrderVO> getOrderList(Long userId) {
    return orderRepository.findByUserId(userId).stream()
        .filter(order -> order.getTotalAmount().compareTo(new BigDecimal("100")) > 0)
        .sorted(Comparator.comparing(Order::getTotalAmount).reversed())
        .map(this::convertToVO)
        .collect(Collectors.toList());
}

private OrderVO convertToVO(Order order) {
    return OrderVO.builder()
        .orderNo(order.getOrderNo())
        .totalAmount(order.getTotalAmount())
        .status(order.getStatus())
        .build();
}
```

### 5.2 分组和聚合

**优雅代码**：

```java
/**
 * 按状态分组统计订单
 */
public Map<OrderStatus, Long> countByStatus(Long userId) {
    return orderRepository.findByUserId(userId).stream()
        .collect(Collectors.groupingBy(
            Order::getStatus,
            Collectors.counting()
        ));
}

/**
 * 按状态分组计算总金额
 */
public Map<OrderStatus, BigDecimal> sumAmountByStatus(Long userId) {
    return orderRepository.findByUserId(userId).stream()
        .collect(Collectors.groupingBy(
            Order::getStatus,
            Collectors.reducing(
                BigDecimal.ZERO,
                Order::getTotalAmount,
                BigDecimal::add
            )
        ));
}

/**
 * 查找最大金额订单
 */
public Optional<Order> findMaxAmountOrder(Long userId) {
    return orderRepository.findByUserId(userId).stream()
        .max(Comparator.comparing(Order::getTotalAmount));
}

/**
 * 计算总金额
 */
public BigDecimal calculateTotalAmount(Long userId) {
    return orderRepository.findByUserId(userId).stream()
        .map(Order::getTotalAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```

---

## 第六章：Optional 优雅处理空值

### 6.1 避免 NullPointerException

**问题代码**：

```java
public String getUserPhone(Long userId) {
    User user = userRepository.findById(userId).orElse(null);
    if (user != null) {
        UserInfo userInfo = user.getUserInfo();
        if (userInfo != null) {
            String phone = userInfo.getPhone();
            if (phone != null) {
                return phone;
            }
        }
    }
    return "未知";
}
```

**优雅代码**：

```java
public String getUserPhone(Long userId) {
    return userRepository.findById(userId)
        .map(User::getUserInfo)
        .map(UserInfo::getPhone)
        .orElse("未知");
}
```

### 6.2 Optional 常用方法

```java
// 创建 Optional
Optional<User> user = Optional.of(new User());           // 不能为 null
Optional<User> user = Optional.ofNullable(getUser());    // 可以为 null
Optional<User> user = Optional.empty();                  // 空 Optional

// 判断是否存在
if (user.isPresent()) {
    System.out.println(user.get());
}

// 存在则执行
user.ifPresent(u -> System.out.println(u.getName()));

// 存在则返回，否则返回默认值
User defaultUser = user.orElse(new User());

// 存在则返回，否则执行函数
User defaultUser = user.orElseGet(() -> createDefaultUser());

// 存在则返回，否则抛出异常
User user = optional.orElseThrow(() -> new UserNotFoundException());

// 过滤
Optional<User> adult = user.filter(u -> u.getAge() >= 18);

// 转换
Optional<String> name = user.map(User::getName);
```

---

## 第七章：Lombok 简化代码

### 7.1 常用注解

```java
/**
 * 订单实体（使用 Lombok）
 */
@Data                    // 生成 getter/setter/toString/equals/hashCode
@Builder                 // 生成 Builder 模式
@NoArgsConstructor      // 生成无参构造
@AllArgsConstructor     // 生成全参构造
@Entity
@Table(name = "t_order")
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String orderNo;
    
    private Long userId;
    
    private Long productId;
    
    private String productName;
    
    private Integer quantity;
    
    private BigDecimal totalAmount;
    
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}

/**
 * 使用 Builder 创建对象
 */
Order order = Order.builder()
    .orderNo("ORD123456")
    .userId(1001L)
    .productId(2001L)
    .productName("iPhone 15 Pro")
    .quantity(1)
    .totalAmount(new BigDecimal("8999"))
    .status(OrderStatus.PENDING)
    .createTime(LocalDateTime.now())
    .updateTime(LocalDateTime.now())
    .build();
```

### 7.2 日志注解

```java
@Service
@Slf4j  // 自动生成 log 对象
public class OrderService {
    
    public void createOrder() {
        log.info("创建订单");
        log.debug("调试信息");
        log.warn("警告信息");
        log.error("错误信息", exception);
    }
}
```

---

## 第八章：代码优化技巧汇总

### 8.1 使用枚举代替魔法值

**问题代码**：

```java
if (order.getStatus().equals("PENDING")) {
    // ...
} else if (order.getStatus().equals("PAID")) {
    // ...
}
```

**优雅代码**：

```java
/**
 * 订单状态枚举
 */
@Getter
@AllArgsConstructor
public enum OrderStatus {
    
    PENDING("待支付"),
    PAID("已支付"),
    PROCESSING("处理中"),
    SHIPPED("已发货"),
    COMPLETED("已完成"),
    CANCELLED("已取消");
    
    private final String description;
}

// 使用
if (order.getStatus() == OrderStatus.PENDING) {
    // ...
} else if (order.getStatus() == OrderStatus.PAID) {
    // ...
}
```

### 8.2 使用常量类

```java
/**
 * 订单常量
 */
public final class OrderConstants {
    
    private OrderConstants() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }
    
    /**
     * 订单前缀
     */
    public static final String ORDER_PREFIX = "ORD";
    
    /**
     * 缓存前缀
     */
    public static final String CACHE_PREFIX = "order:";
    
    /**
     * 缓存过期时间（秒）
     */
    public static final long CACHE_EXPIRE = 600L;
    
    /**
     * 最大购买数量
     */
    public static final int MAX_QUANTITY = 999;
}
```

### 8.3 使用断言简化校验

```java
// Spring Assert
Assert.notNull(userId, "用户ID不能为空");
Assert.isTrue(quantity > 0, "购买数量必须大于0");
Assert.hasText(orderNo, "订单号不能为空");
Assert.notEmpty(items, "订单明细不能为空");
```

### 8.4 避免过深的嵌套

**问题代码**：

```java
if (user != null) {
    if (user.isActive()) {
        if (user.getBalance().compareTo(amount) >= 0) {
            if (product != null) {
                if (product.isOnSale()) {
                    // 业务逻辑
                }
            }
        }
    }
}
```

**优雅代码（卫语句）**：

```java
// 提前返回
if (user == null) {
    throw new BusinessException(ErrorCode.USER_NOT_FOUND);
}

if (!user.isActive()) {
    throw new BusinessException(ErrorCode.USER_INACTIVE);
}

if (user.getBalance().compareTo(amount) < 0) {
    throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
}

if (product == null) {
    throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
}

if (!product.isOnSale()) {
    throw new BusinessException(ErrorCode.PRODUCT_OFF_SALE);
}

// 业务逻辑
```

---

## 第九章：学习计划

### Week 1: 代码重构
- [ ] 识别代码坏味道
- [ ] 提取长方法
- [ ] 消除重复代码
- [ ] 简化条件判断
- [ ] 重构现有项目

### Week 2: 异常和日志
- [ ] 统一异常处理
- [ ] 自定义业务异常
- [ ] 参数校验优化
- [ ] 日志规范
- [ ] AOP 日志切面

### Week 3: 设计模式
- [ ] 工厂模式
- [ ] 策略模式
- [ ] 模板方法模式
- [ ] 责任链模式
- [ ] 应用到项目中

### Week 4: 优雅编程
- [ ] Stream API
- [ ] Optional
- [ ] Lombok
- [ ] 常量和枚举
- [ ] 代码优化技巧

---

## 总结

完成本指南后，你将掌握：

### 代码能力提升
- ✅ 识别和重构代码坏味道
- ✅ 优雅的异常处理
- ✅ 规范的日志记录
- ✅ 常用设计模式

### 编程技巧
- ✅ Stream API 流式编程
- ✅ Optional 处理空值
- ✅ Lombok 简化代码
- ✅ 各种优化技巧

### 预期效果
- 代码行数减少 30%+
- Bug 率降低 50%+
- 可读性提升 80%+
- 达到中级工程师代码水平

---

**下一步**：查看《电商微服务代码规范》，按照规范重构项目代码！🚀

