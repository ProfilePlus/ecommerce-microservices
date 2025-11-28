/**
 * 订单相关API
 */
import request from './request'
import type { OrderRequest, OrderResponse } from '@/types/order'

/**
 * 创建订单
 * @param data 订单请求数据
 */
export const createOrder = (data: OrderRequest) => {
  return request<OrderResponse>({
    url: '/orders',
    method: 'POST',
    data
  })
}

/**
 * 查询订单详情
 * @param orderNo 订单号
 */
export const getOrder = (orderNo: string) => {
  return request<OrderResponse>({
    url: `/orders/${orderNo}`,
    method: 'GET'
  })
}

/**
 * 查询用户订单列表
 * @param userId 用户ID
 */
export const getUserOrders = (userId: number) => {
  return request<OrderResponse[]>({
    url: `/orders/user/${userId}`,
    method: 'GET'
  })
}

/**
 * 健康检查
 */
export const healthCheck = () => {
  return request<string>({
    url: '/orders/health',
    method: 'GET'
  })
}

