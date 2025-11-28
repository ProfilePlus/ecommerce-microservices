/**
 * 订单相关类型定义
 */

/**
 * 订单请求类型
 */
export interface OrderRequest {
  userId: number
  productId: number
  productName: string
  quantity: number
  totalAmount: number
}

/**
 * 订单响应类型
 */
export interface OrderResponse {
  orderNo: string
  userId: number
  productId: number
  productName: string
  quantity: number
  totalAmount: number
  status: string
  createTime: string
}

/**
 * 订单状态枚举
 */
export enum OrderStatus {
  PENDING = 'PENDING',           // 待处理
  PROCESSING = 'PROCESSING',     // 处理中
  COMPLETED = 'COMPLETED',       // 已完成
  CANCELLED = 'CANCELLED'        // 已取消
}

/**
 * 订单状态显示映射
 */
export const OrderStatusMap: Record<OrderStatus, { label: string; type: string }> = {
  [OrderStatus.PENDING]: { label: '待处理', type: 'warning' },
  [OrderStatus.PROCESSING]: { label: '处理中', type: 'primary' },
  [OrderStatus.COMPLETED]: { label: '已完成', type: 'success' },
  [OrderStatus.CANCELLED]: { label: '已取消', type: 'info' }
}

