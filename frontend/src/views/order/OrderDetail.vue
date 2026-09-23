<template>
  <div class="order-detail-page" v-loading="loading">
    <div class="page-header">
      <el-button text @click="$router.push(backPath)">
        <el-icon><ArrowLeft /></el-icon>
        返回订单
      </el-button>
      <el-tag v-if="order" :type="statusType">{{ statusText }}</el-tag>
    </div>

    <el-empty v-if="!loading && !order" description="订单不存在">
      <el-button type="primary" @click="$router.push(backPath)">返回订单列表</el-button>
    </el-empty>

    <template v-else-if="order">
      <el-card shadow="never" class="detail-card">
        <template #header>
          <div class="card-title">
            <span>订单信息</span>
            <span class="order-no">{{ order.orderNo }}</span>
          </div>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="收货人">{{ order.receiver }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ order.phone }}</el-descriptions-item>
          <el-descriptions-item label="收货地址" :span="2">{{ order.address }}</el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ formatTime(order.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="订单金额">¥{{ formatPrice(order.realAmount) }}</el-descriptions-item>
          <el-descriptions-item v-if="order.remark" label="备注" :span="2">{{ order.remark }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never" class="detail-card">
        <template #header>商品明细</template>
        <div v-for="item in items" :key="item.id" class="order-item">
          <el-image :src="item.productImage" fit="cover" class="item-image">
            <template #error>
              <div class="image-placeholder"><el-icon><Picture /></el-icon></div>
            </template>
          </el-image>
          <div class="item-info">
            <p class="item-name">{{ item.productName }}</p>
            <p class="item-spec">{{ item.skuDesc || '默认规格' }}</p>
            <p class="item-price">¥{{ formatPrice(item.price) }} × {{ item.quantity }}</p>
          </div>
          <span class="item-total">¥{{ formatPrice(item.totalPrice) }}</span>
        </div>
        <div class="order-total">
          <span>应付总额</span>
          <strong>¥{{ formatPrice(order.realAmount) }}</strong>
        </div>
      </el-card>

      <div class="actions">
        <el-button v-if="order.status === 0" type="primary" @click="handlePay">立即支付</el-button>
        <el-button v-if="order.status === 0" type="danger" plain @click="handleCancel">取消订单</el-button>
        <el-button v-if="order.status === 2" type="primary" @click="handleConfirm">确认收货</el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Picture } from '@element-plus/icons-vue'
import { cancelOrder, confirmOrder, getOrderDetail, payOrder } from '@/api/orderApi'
import { ORDER_STATUS_MAP } from '@/utils/constants'

const route = useRoute()
const order = ref<any>(null)
const items = ref<any[]>([])
const loading = ref(false)
const backPath = computed(() => route.path.startsWith('/admin') ? '/admin/order' : '/order')

const onlyOwned = computed(() => route.query.mine !== '0')
const statusText = computed(() => {
  const status = order.value?.status
  return status === undefined ? '-' : ORDER_STATUS_MAP[status as keyof typeof ORDER_STATUS_MAP] || '-'
})
const statusType = computed(() => {
  const map: Record<number, string> = { 0: 'warning', 1: '', 2: 'primary', 3: 'success', 4: 'info' }
  return order.value?.status === undefined ? 'info' : map[order.value.status] || 'info'
})

const formatPrice = (price: number | string) => Number(price || 0).toFixed(2)
const formatTime = (value: any) => {
  if (!value) return '-'
  if (Array.isArray(value)) {
    const [year, month, day, hour, minute, second] = value
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${year}-${pad(month)}-${pad(day)} ${pad(hour)}:${pad(minute)}:${pad(second)}`
  }
  return String(value).replace('T', ' ').slice(0, 19)
}

const loadDetail = async () => {
  loading.value = true
  try {
    const res = await getOrderDetail(Number(route.params.id), { mine: onlyOwned.value })
    order.value = res.data?.order || null
    items.value = res.data?.items || []
  } catch (e: any) {
    order.value = null
    ElMessage.error(e.message || '订单加载失败')
  } finally {
    loading.value = false
  }
}

const handleCancel = async () => {
  try {
    await ElMessageBox.confirm('确定要取消该订单吗？', '提示', { type: 'warning' })
    await cancelOrder(order.value.id, { mine: true })
    ElMessage.success('订单已取消')
    await loadDetail()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '取消失败')
  }
}

const handlePay = async () => {
  try {
    await ElMessageBox.confirm(`确认支付 ¥${formatPrice(order.value.realAmount)} 吗？`, '模拟支付', {
      type: 'warning',
      confirmButtonText: '确认支付'
    })
    await payOrder(order.value.id, { mine: onlyOwned.value })
    ElMessage.success('支付成功')
    await loadDetail()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '支付失败')
  }
}

const handleConfirm = async () => {
  try {
    await ElMessageBox.confirm('确认已经收到商品吗？', '提示', { type: 'warning' })
    await confirmOrder(order.value.id, { mine: true })
    ElMessage.success('已确认收货')
    await loadDetail()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.order-detail-page {
  padding-bottom: 24px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.detail-card {
  margin-bottom: 16px;
  border-radius: 8px;
}

.card-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.order-no {
  color: #909399;
  font-size: 13px;
}

.order-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 0;
  border-bottom: 1px solid #ebeef5;
}

.item-image {
  width: 76px;
  height: 76px;
  border-radius: 8px;
  flex-shrink: 0;
  background: #f5f7fa;
}

.image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
}

.item-info {
  flex: 1;
}

.item-name {
  font-weight: 600;
  margin-bottom: 6px;
}

.item-spec,
.item-price {
  color: #909399;
  font-size: 13px;
}

.item-total,
.order-total strong {
  color: #e4393c;
  font-weight: 700;
}

.order-total {
  display: flex;
  justify-content: flex-end;
  align-items: baseline;
  gap: 16px;
  padding-top: 18px;
}

.order-total strong {
  font-size: 22px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 768px) {
  .order-detail-page :deep(.el-descriptions__body .el-descriptions__table) {
    display: block;
  }

  .order-item {
    align-items: flex-start;
  }
}
</style>
