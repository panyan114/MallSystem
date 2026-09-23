<template>
  <div class="order-page">
    <div class="page-header">
      <h2>我的订单</h2>
      <el-button @click="loadOrders">刷新</el-button>
    </div>
    <el-tabs v-model="activeTab" @tab-change="loadOrders">
      <el-tab-pane
        v-for="tab in tabs"
        :key="tab.name"
        :label="tab.label"
        :name="tab.name"
      />
    </el-tabs>
    <el-table v-loading="loading" :data="orderList" stripe empty-text="暂无订单">
      <el-table-column prop="orderNo" label="订单编号" min-width="200" />
      <el-table-column label="金额" width="120">
        <template #default="{ row }">¥{{ formatPrice(row.realAmount || row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下单时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="$router.push(`/order/detail/${row.id}`)">查看</el-button>
          <el-button v-if="row.status === 0" size="small" type="primary" @click="handlePay(row)">
            支付
          </el-button>
          <el-button v-if="row.status === 0" size="small" type="danger" text @click="handleCancel(row)">
            取消订单
          </el-button>
          <el-button v-if="row.status === 2" size="small" type="primary" text @click="handleConfirm(row)">
            确认收货
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder, confirmOrder, getOrderList, payOrder } from '@/api/orderApi'
import { ORDER_STATUS_MAP } from '@/utils/constants'

const orderList = ref<any[]>([])
const activeTab = ref('all')
const loading = ref(false)
const tabs = [
  { label: '全部', name: 'all' },
  { label: '待付款', name: '0' },
  { label: '待发货', name: '1' },
  { label: '待收货', name: '2' },
  { label: '已完成', name: '3' },
  { label: '已取消', name: '4' }
]

const getStatusText = (status: number) => ORDER_STATUS_MAP[status] || '-'
const getStatusType = (status: number) => {
  const map: Record<number, string> = { 0: 'warning', 1: '', 2: 'primary', 3: 'success', 4: 'info' }
  return map[status] || 'info'
}
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

const loadOrders = async () => {
  loading.value = true
  try {
    const params: any = { mine: true }
    if (activeTab.value !== 'all') params.status = Number(activeTab.value)
    const res = await getOrderList(params)
    orderList.value = res.data?.list || []
  } catch (e: any) {
    ElMessage.error(e.message || '订单加载失败')
  } finally {
    loading.value = false
  }
}

const handleCancel = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定要取消该订单吗？', '提示', { type: 'warning' })
    await cancelOrder(row.id, { mine: true })
    ElMessage.success('订单已取消')
    await loadOrders()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '取消失败')
  }
}

const handlePay = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确认支付 ¥${formatPrice(row.realAmount || row.totalAmount)} 吗？`, '模拟支付', {
      type: 'warning',
      confirmButtonText: '确认支付'
    })
    await payOrder(row.id, { mine: true })
    ElMessage.success('支付成功')
    await loadOrders()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '支付失败')
  }
}

const handleConfirm = async (row: any) => {
  try {
    await ElMessageBox.confirm('确认已经收到商品吗？', '提示', { type: 'warning' })
    await confirmOrder(row.id, { mine: true })
    ElMessage.success('已确认收货')
    await loadOrders()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

onMounted(loadOrders)
</script>

<style scoped>
.order-page {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-header h2 {
  margin: 0 0 12px;
}

@media (max-width: 768px) {
  .order-page {
    padding: 0;
  }
}
</style>
