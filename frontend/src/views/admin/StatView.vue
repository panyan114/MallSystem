<template>
  <div v-loading="loading" class="stat-page">
    <div class="stat-heading">
      <div>
        <h2>数据统计</h2>
        <p>销售、订单和商品表现汇总</p>
      </div>
      <el-button :loading="loading" @click="loadStats">刷新</el-button>
    </div>

    <section class="metric-grid">
      <article v-for="item in metrics" :key="item.label" class="metric-item">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.hint }}</small>
      </article>
    </section>

    <el-card class="ranking-card" shadow="never">
      <template #header>
        <div class="card-heading">
          <span>商品销量排行</span>
          <small>按累计销量排序</small>
        </div>
      </template>
      <el-table :data="ranking" stripe>
        <el-table-column type="index" label="排名" width="72" />
        <el-table-column prop="name" label="商品名称" min-width="220" />
        <el-table-column label="售价" width="120">
          <template #default="{ row }">¥{{ formatMoney(row.price) }}</template>
        </el-table-column>
        <el-table-column prop="sales" label="销量" width="100" />
        <el-table-column prop="stock" label="库存" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '上架中' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无商品排行数据" :image-size="80" />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDashboard, getProductRanking } from '@/api/adminApi'

const loading = ref(false)
const dashboard = ref<any>({})
const ranking = ref<any[]>([])

const formatMoney = (value: number | string | undefined) => Number(value || 0).toFixed(2)

const metrics = computed(() => [
  { label: '今日销售额', value: `¥${formatMoney(dashboard.value.todaySales)}`, hint: '已支付订单' },
  { label: '今日订单数', value: String(dashboard.value.todayOrders || 0), hint: '今日创建订单' },
  { label: '本月销售额', value: `¥${formatMoney(dashboard.value.monthSales)}`, hint: '自然月累计' },
  { label: '本月订单数', value: String(dashboard.value.monthOrders || 0), hint: '自然月累计' },
  { label: '平均客单价', value: `¥${formatMoney(dashboard.value.averageOrderAmount)}`, hint: '已支付订单' },
  { label: '累计销售额', value: `¥${formatMoney(dashboard.value.totalSales)}`, hint: '全部已支付订单' }
])

const loadStats = async () => {
  loading.value = true
  try {
    const [dashboardRes, rankingRes] = await Promise.all([getDashboard(), getProductRanking()])
    dashboard.value = dashboardRes.data || {}
    ranking.value = rankingRes.data || []
  } catch (error: any) {
    ElMessage.error(error.message || '统计数据加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadStats)
</script>

<style scoped>
.stat-page {
  min-height: 360px;
}

.stat-heading,
.card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.stat-heading {
  margin-bottom: 18px;
}

.stat-heading h2 {
  margin: 0 0 4px;
  color: #263445;
  font-size: 21px;
}

.stat-heading p,
.card-heading small {
  margin: 0;
  color: #8a96a3;
  font-size: 12px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.metric-item {
  min-width: 0;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 7px;
  border: 1px solid #e8edf2;
  border-radius: 10px;
  background: #fff;
}

.metric-item span {
  color: #7d8996;
  font-size: 13px;
}

.metric-item strong {
  color: #253243;
  font-size: 24px;
  line-height: 1.2;
}

.metric-item small {
  color: #9aa4af;
  font-size: 11px;
}

.ranking-card {
  border-radius: 10px;
}

.card-heading span {
  color: #2d3a48;
  font-size: 16px;
  font-weight: 700;
}

@media (max-width: 800px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
