<template>
  <div class="create-order">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>创建订单</h2>
      <p>填写订单信息，提交后将自动扣减库存并发送通知</p>
    </div>

    <!-- 订单表单 -->
    <el-card class="form-card" shadow="never">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
        size="large"
      >
        <el-form-item label="用户ID" prop="userId">
          <el-input-number
            v-model="form.userId"
            :min="1"
            :max="9999"
            placeholder="请输入用户ID"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="商品ID" prop="productId">
          <el-input-number
            v-model="form.productId"
            :min="1"
            :max="9999"
            placeholder="请输入商品ID"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="商品名称" prop="productName">
          <el-input
            v-model="form.productName"
            placeholder="请输入商品名称"
            clearable
          />
        </el-form-item>

        <el-form-item label="购买数量" prop="quantity">
          <el-input-number
            v-model="form.quantity"
            :min="1"
            :max="999"
            placeholder="请输入购买数量"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="订单金额" prop="totalAmount">
          <el-input-number
            v-model="form.totalAmount"
            :min="0.01"
            :precision="2"
            :step="0.01"
            placeholder="请输入订单金额"
            style="width: 100%"
          />
          <template #append>元</template>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" size="large" @click="handleSubmit" :loading="loading">
            <el-icon class="mr-1"><Check /></el-icon>
            提交订单
          </el-button>
          <el-button size="large" @click="handleReset">
            <el-icon class="mr-1"><RefreshLeft /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 示例数据提示 -->
    <el-card class="tips-card" shadow="never">
      <template #header>
        <div class="card-header">
          <el-icon color="#409EFF"><InfoFilled /></el-icon>
          <span>测试数据参考</span>
        </div>
      </template>
      <div class="tips-content">
        <p>• 用户ID: 1001</p>
        <p>• 商品ID: 1001（请确保库存服务中有该商品的库存数据）</p>
        <p>• 商品名称: iPhone 15 Pro</p>
        <p>• 购买数量: 2</p>
        <p>• 订单金额: 16998.00</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import { createOrder } from '@/api/order'
import type { OrderRequest } from '@/types/order'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

// 表单数据
const form = reactive<OrderRequest>({
  userId: 1001,
  productId: 1001,
  productName: 'iPhone 15 Pro',
  quantity: 2,
  totalAmount: 16998.00
})

// 表单验证规则
const rules: FormRules = {
  userId: [
    { required: true, message: '请输入用户ID', trigger: 'blur' }
  ],
  productId: [
    { required: true, message: '请输入商品ID', trigger: 'blur' }
  ],
  productName: [
    { required: true, message: '请输入商品名称', trigger: 'blur' },
    { min: 2, max: 50, message: '商品名称长度在 2 到 50 个字符', trigger: 'blur' }
  ],
  quantity: [
    { required: true, message: '请输入购买数量', trigger: 'blur' }
  ],
  totalAmount: [
    { required: true, message: '请输入订单金额', trigger: 'blur' }
  ]
}

// 提交订单
const handleSubmit = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const response = await createOrder(form)
        ElMessage.success('订单创建成功！')
        
        // 跳转到订单详情页
        setTimeout(() => {
          router.push(`/orders/${response.orderNo}`)
        }, 1000)
      } catch (error) {
        console.error('创建订单失败:', error)
      } finally {
        loading.value = false
      }
    }
  })
}

// 重置表单
const handleReset = () => {
  formRef.value?.resetFields()
}
</script>

<style scoped>
.create-order {
  max-width: 800px;
  margin: 0 auto;
}

.page-header {
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

.form-card {
  margin-bottom: 20px;
}

.tips-card {
  background: #f0f9ff;
  border: 1px solid #b3d8ff;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #409EFF;
}

.tips-content {
  line-height: 1.8;
  color: #606266;
}

.tips-content p {
  margin: 4px 0;
}

.mr-1 {
  margin-right: 4px;
}
</style>

