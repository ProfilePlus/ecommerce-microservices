<template>
  <div class="order-detail">
    <!-- 返回按钮 -->
    <el-button class="back-btn" @click="handleBack">
      <el-icon class="mr-1"><ArrowLeft /></el-icon>
      返回列表
    </el-button>

    <!-- 加载状态 -->
    <div v-loading="loading" class="loading-container" v-if="loading">
      <el-empty description="正在加载订单详情..." />
    </div>

    <!-- 订单详情 -->
    <template v-else-if="order">
      <!-- 订单状态卡片 -->
      <el-card class="status-card" shadow="never">
        <div class="status-content">
          <div class="status-icon">
            <el-icon :size="48" :color="getStatusColor(order.status)">
              <SuccessFilled v-if="order.status === 'COMPLETED'" />
              <Loading v-else-if="order.status === 'PROCESSING'" />
              <Clock v-else-if="order.status === 'PENDING'" />
              <CircleClose v-else />
            </el-icon>
          </div>
          <div class="status-info">
            <h2>{{ getStatusLabel(order.status) }}</h2>
            <p>订单号：{{ order.orderNo }}</p>
          </div>
        </div>
      </el-card>

      <!-- 订单信息卡片 -->
      <el-card class="info-card" shadow="never">
        <template #header>
          <div class="card-header">
            <el-icon><Document /></el-icon>
            <span>订单信息</span>
          </div>
        </template>

        <el-descriptions :column="2" size="large" border>
          <el-descriptions-item label="订单号">
            <el-tag type="info">{{ order.orderNo }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <el-tag :type="getStatusType(order.status)">
              {{ getStatusLabel(order.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="用户ID">
            {{ order.userId }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatDateTime(order.createTime) }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 商品信息卡片 -->
      <el-card class="info-card" shadow="never">
        <template #header>
          <div class="card-header">
            <el-icon><ShoppingBag /></el-icon>
            <span>商品信息</span>
          </div>
        </template>

        <el-descriptions :column="1" size="large" border>
          <el-descriptions-item label="商品ID">
            {{ order.productId }}
          </el-descriptions-item>
          <el-descriptions-item label="商品名称">
            <span class="product-name">{{ order.productName }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="购买数量">
            <span class="quantity">{{ order.quantity }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="订单金额">
            <span class="amount">¥{{ order.totalAmount.toFixed(2) }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 流程说明卡片 -->
      <el-card class="tips-card" shadow="never">
        <template #header>
          <div class="card-header">
            <el-icon color="#409EFF"><InfoFilled /></el-icon>
            <span>处理流程</span>
          </div>
        </template>
        <el-steps :active="getStepActive(order.status)" finish-status="success">
          <el-step title="订单创建" description="订单已提交" />
          <el-step title="库存扣减" description="正在扣减库存" />
          <el-step title="发送通知" description="短信/邮件通知" />
          <el-step title="订单完成" description="处理完成" />
        </el-steps>
      </el-card>
    </template>

    <!-- 订单不存在 -->
    <el-card v-else shadow="never">
      <el-empty description="订单不存在或已被删除" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOrder } from '@/api/order'
import type { OrderResponse } from '@/types/order'
import { OrderStatusMap } from '@/types/order'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const order = ref<OrderResponse | null>(null)

// 获取订单详情
const fetchOrderDetail = async () => {
  const orderNo = route.params.orderNo as string
  if (!orderNo) {
    ElMessage.error('订单号不能为空')
    return
  }

  loading.value = true
  try {
    const response = await getOrder(orderNo)
    order.value = response
  } catch (error) {
    console.error('获取订单详情失败:', error)
    order.value = null
  } finally {
    loading.value = false
  }
}

// 返回列表
const handleBack = () => {
  router.push('/orders')
}

// 获取状态标签
const getStatusLabel = (status: string) => {
  return OrderStatusMap[status as keyof typeof OrderStatusMap]?.label || status
}

// 获取状态类型
const getStatusType = (status: string) => {
  return OrderStatusMap[status as keyof typeof OrderStatusMap]?.type || 'info'
}

// 获取状态颜色
const getStatusColor = (status: string) => {
  const colorMap: Record<string, string> = {
    PENDING: '#E6A23C',
    PROCESSING: '#409EFF',
    COMPLETED: '#67C23A',
    CANCELLED: '#909399'
  }
  return colorMap[status] || '#909399'
}

// 获取步骤激活状态
const getStepActive = (status: string) => {
  const stepMap: Record<string, number> = {
    PENDING: 1,
    PROCESSING: 2,
    COMPLETED: 4,
    CANCELLED: 0
  }
  return stepMap[status] || 0
}

// 格式化日期时间
const formatDateTime = (dateTime: string) => {
  if (!dateTime) return '-'
  return new Date(dateTime).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 页面加载时获取订单详情
onMounted(() => {
  fetchOrderDetail()
})
</script>

<style scoped>
.order-detail {
  max-width: 1000px;
  margin: 0 auto;
}

.back-btn {
  margin-bottom: 20px;
}

.loading-container {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.status-card {
  margin-bottom: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.status-card :deep(.el-card__body) {
  padding: 40px;
}

.status-content {
  display: flex;
  align-items: center;
  gap: 24px;
}

.status-icon {
  flex-shrink: 0;
}

.status-info h2 {
  font-size: 28px;
  font-weight: 600;
  margin-bottom: 8px;
  color: white;
}

.status-info p {
  font-size: 14px;
  opacity: 0.9;
  color: white;
}

.info-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 16px;
}

.product-name {
  font-weight: 600;
  color: #303133;
}

.quantity {
  font-size: 18px;
  font-weight: 600;
  color: #409EFF;
}

.amount {
  color: #f56c6c;
  font-weight: 600;
  font-size: 20px;
}

.tips-card {
  background: #f0f9ff;
  border: 1px solid #b3d8ff;
}

.mr-1 {
  margin-right: 4px;
}
</style>

