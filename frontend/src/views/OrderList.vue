<template>
  <div class="order-list">
    <!-- 页面标题和操作栏 -->
    <div class="page-header">
      <div>
        <h2>订单列表</h2>
        <p>查看和管理所有订单信息</p>
      </div>
      <el-button type="primary" size="large" @click="handleCreate">
        <el-icon class="mr-1"><Plus /></el-icon>
        创建订单
      </el-button>
    </div>

    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" size="large">
        <el-form-item label="用户ID">
          <el-input-number
            v-model="searchForm.userId"
            :min="1"
            placeholder="请输入用户ID"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon class="mr-1"><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon class="mr-1"><RefreshLeft /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 订单列表 -->
    <el-card shadow="never">
      <el-table
        v-loading="loading"
        :data="orderList"
        style="width: 100%"
        size="large"
        stripe
      >
        <el-table-column prop="orderNo" label="订单号" min-width="200" />
        <el-table-column prop="userId" label="用户ID" width="100" align="center" />
        <el-table-column prop="productName" label="商品名称" min-width="180" />
        <el-table-column prop="quantity" label="数量" width="80" align="center" />
        <el-table-column prop="totalAmount" label="订单金额" width="120" align="right">
          <template #default="{ row }">
            <span class="amount">¥{{ row.totalAmount.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleView(row.orderNo)">
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空状态 -->
      <el-empty v-if="!loading && orderList.length === 0" description="暂无订单数据" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getUserOrders } from '@/api/order'
import type { OrderResponse } from '@/types/order'
import { OrderStatusMap } from '@/types/order'

const router = useRouter()
const loading = ref(false)
const orderList = ref<OrderResponse[]>([])

// 搜索表单
const searchForm = reactive({
  userId: 1001
})

// 查询订单
const handleSearch = async () => {
  if (!searchForm.userId) {
    ElMessage.warning('请输入用户ID')
    return
  }

  loading.value = true
  try {
    const response = await getUserOrders(searchForm.userId)
    orderList.value = response
    if (response.length === 0) {
      ElMessage.info('该用户暂无订单')
    }
  } catch (error) {
    console.error('查询订单失败:', error)
    orderList.value = []
  } finally {
    loading.value = false
  }
}

// 重置搜索
const handleReset = () => {
  searchForm.userId = 1001
  orderList.value = []
}

// 创建订单
const handleCreate = () => {
  router.push('/orders/create')
}

// 查看订单详情
const handleView = (orderNo: string) => {
  router.push(`/orders/${orderNo}`)
}

// 获取状态标签
const getStatusLabel = (status: string) => {
  return OrderStatusMap[status as keyof typeof OrderStatusMap]?.label || status
}

// 获取状态类型
const getStatusType = (status: string) => {
  return OrderStatusMap[status as keyof typeof OrderStatusMap]?.type || 'info'
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

// 页面加载时自动查询
onMounted(() => {
  handleSearch()
})
</script>

<style scoped>
.order-list {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.page-header p {
  font-size: 14px;
  color: #909399;
}

.search-card {
  margin-bottom: 20px;
}

.amount {
  color: #f56c6c;
  font-weight: 600;
  font-size: 15px;
}

.mr-1 {
  margin-right: 4px;
}
</style>

