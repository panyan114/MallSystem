<template>
  <div class="order-manage-page">
    <el-card>
      <div class="toolbar">
        <el-select v-model="filterStatus" placeholder="订单状态" clearable style="width: 150px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" @click="loadOrders">查询</el-button>
      </div>
      <el-table :data="orderList" stripe border>
        <el-table-column prop="orderNo" label="订单编号" width="200" />
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="totalAmount" label="总金额" width="120">
          <template #default="{ row }">¥{{ row.totalAmount }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="下单时间" width="180" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row.id)">详情</el-button>
            <el-button v-if="row.status === 1" size="small" type="primary" @click="shipOrder(row.id)">发货</el-button>
            <el-button v-if="row.status === 0" size="small" type="danger" @click="cancelOrder(row.id)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOrderList, cancelOrder, shipOrder as shipOrderApi } from '@/api/orderApi'
import { ORDER_STATUS_MAP } from '@/utils/constants'

const orderList = ref<any[]>([])
const filterStatus = ref<number | undefined>(undefined)
const router = useRouter()

const statusOptions = [
  { value: 0, label: '待付款' },
  { value: 1, label: '待发货' },
  { value: 2, label: '待收货' },
  { value: 3, label: '已完成' },
  { value: 4, label: '已取消' }
]

const loadOrders = async () => {
  const res = await getOrderList({ status: filterStatus.value })
  orderList.value = res.data?.list || []
}

const getStatusText = (status: number) => ORDER_STATUS_MAP[status] || '-'
const getStatusType = (status: number) => {
  const map: Record<number, string> = { 0: 'warning', 1: '', 2: 'primary', 3: 'success', 4: 'info' }
  return map[status] || 'info'
}

const viewDetail = (id: number) => {
  router.push(`/admin/order/${id}?mine=0`)
}

const shipOrder = async (id: number) => {
  try {
    await shipOrderApi(id, { logisticsCompany: '', logisticsNo: '' })
    ElMessage.success('订单已发货')
    await loadOrders()
  } catch (e: any) {
    ElMessage.error(e.message || '发货失败')
  }
}

const cancelOrder = async (id: number) => {
  try {
    await cancelOrder(id)
    loadOrders()
    ElMessage.success('订单已取消')
  } catch {}
}

onMounted(() => { loadOrders() })
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
