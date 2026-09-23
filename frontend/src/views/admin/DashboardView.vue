<template>
  <div v-loading="loading" class="dashboard-page">
    <div class="section-heading">
      <div>
        <h2>经营概览</h2>
        <p>关键经营数据实时汇总</p>
      </div>
      <el-button :loading="loading" @click="loadDashboard">刷新数据</el-button>
    </div>

    <div class="stats-grid">
      <article v-for="item in stats" :key="item.title" class="stat-card" :class="item.tone">
        <div class="stat-icon">
          <el-icon><component :is="item.icon" /></el-icon>
        </div>
        <div class="stat-copy">
          <span>{{ item.title }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.hint }}</small>
        </div>
      </article>
    </div>

    <div class="dashboard-grid">
      <section class="panel trend-panel">
        <header class="panel-header">
          <div>
            <h3>近 7 日销售趋势</h3>
            <p>已支付订单实付金额</p>
          </div>
          <span class="panel-total">¥{{ formatMoney(dashboard.totalSales) }}</span>
        </header>

        <div v-if="hasTrendData" class="trend-chart">
          <div v-for="item in dashboard.salesTrend" :key="item.date" class="trend-column">
            <span class="trend-value">¥{{ formatCompactMoney(item.amount) }}</span>
            <div class="bar-track">
              <div class="bar-fill" :style="{ height: trendHeight(item.amount) }" />
            </div>
            <span class="trend-date">{{ item.date }}</span>
          </div>
        </div>
        <el-empty v-else description="近 7 日暂无成交数据" :image-size="80" />
      </section>

      <section class="panel status-panel">
        <header class="panel-header">
          <div>
            <h3>订单状态</h3>
            <p>全部订单处理进度</p>
          </div>
          <span class="panel-count">{{ dashboard.totalOrders }} 单</span>
        </header>

        <div v-if="dashboard.totalOrders > 0" class="status-list">
          <div v-for="item in dashboard.orderStatus" :key="item.status" class="status-item">
            <div class="status-copy">
              <span class="status-name">
                <i :class="`status-dot status-${item.status}`" />
                {{ item.name }}
              </span>
              <strong>{{ item.count }}</strong>
            </div>
            <div class="progress-track">
              <div
                :class="`progress-fill status-${item.status}`"
                :style="{ width: statusWidth(item.count) }"
              />
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无订单数据" :image-size="80" />
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Goods, List, Money, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getDashboard } from '@/api/adminApi'

const loading = ref(false)
const dashboard = ref<any>({
  totalSales: 0,
  totalOrders: 0,
  totalUsers: 0,
  totalProducts: 0,
  todaySales: 0,
  todayOrders: 0,
  salesTrend: [],
  orderStatus: []
})

const formatMoney = (value: number | string | undefined) => Number(value || 0).toFixed(2)
const formatCompactMoney = (value: number | string | undefined) => {
  const amount = Number(value || 0)
  return amount >= 10000 ? `${(amount / 10000).toFixed(1)}万` : amount.toFixed(0)
}

const stats = computed(() => [
  {
    title: '总销售额',
    value: `¥${formatMoney(dashboard.value.totalSales)}`,
    hint: `今日 ¥${formatMoney(dashboard.value.todaySales)}`,
    icon: Money,
    tone: 'orange'
  },
  {
    title: '总订单数',
    value: String(dashboard.value.totalOrders || 0),
    hint: `今日 ${dashboard.value.todayOrders || 0} 单`,
    icon: List,
    tone: 'blue'
  },
  {
    title: '总用户数',
    value: String(dashboard.value.totalUsers || 0),
    hint: '当前启用账号',
    icon: User,
    tone: 'green'
  },
  {
    title: '商品总数',
    value: String(dashboard.value.totalProducts || 0),
    hint: '店铺在库商品',
    icon: Goods,
    tone: 'violet'
  }
])

const trendMax = computed(() => {
  return Math.max(0, ...dashboard.value.salesTrend.map((item: any) => Number(item.amount || 0)))
})

const hasTrendData = computed(() => trendMax.value > 0)

const statusMax = computed(() => {
  return Math.max(1, ...dashboard.value.orderStatus.map((item: any) => Number(item.count || 0)))
})

const trendHeight = (value: number | string) => {
  if (trendMax.value <= 0) return '0%'
  return `${Math.max(5, Math.round((Number(value || 0) / trendMax.value) * 100))}%`
}

const statusWidth = (value: number | string) => {
  return `${Math.round((Number(value || 0) / statusMax.value) * 100)}%`
}

const loadDashboard = async () => {
  loading.value = true
  try {
    const res = await getDashboard()
    dashboard.value = {
      ...dashboard.value,
      ...(res.data || {}),
      salesTrend: res.data?.salesTrend || [],
      orderStatus: res.data?.orderStatus || []
    }
  } catch (error: any) {
    ElMessage.error(error.message || '数据概览加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<style scoped>
.dashboard-page {
  min-height: 360px;
}

.section-heading,
.panel-header,
.status-copy {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-heading {
  gap: 16px;
  margin-bottom: 18px;
}

.section-heading h2 {
  margin: 0 0 4px;
  color: #263445;
  font-size: 21px;
}

.section-heading p,
.panel-header p {
  margin: 0;
  color: #8a96a3;
  font-size: 12px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

.stat-card {
  min-width: 0;
  padding: 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  border: 1px solid #e8edf2;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 4px 14px rgba(31, 45, 61, 0.04);
}

.stat-icon {
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 10px;
  font-size: 22px;
}

.stat-card.orange .stat-icon {
  background: #fff0e9;
  color: #ed6b36;
}

.stat-card.blue .stat-icon {
  background: #eaf2ff;
  color: #3978d6;
}

.stat-card.green .stat-icon {
  background: #eaf8f0;
  color: #2f9e62;
}

.stat-card.violet .stat-icon {
  background: #f0edff;
  color: #7357d9;
}

.stat-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.stat-copy > span {
  color: #7e8a98;
  font-size: 13px;
}

.stat-copy strong {
  color: #243141;
  font-size: 25px;
  line-height: 1.2;
  white-space: nowrap;
}

.stat-copy small {
  color: #9aa4af;
  font-size: 11px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(300px, 1fr);
  gap: 18px;
}

.panel {
  min-width: 0;
  padding: 20px;
  border: 1px solid #e8edf2;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 4px 14px rgba(31, 45, 61, 0.04);
}

.panel-header {
  gap: 16px;
  margin-bottom: 24px;
}

.panel-header h3 {
  margin: 0 0 4px;
  color: #2a3746;
  font-size: 16px;
}

.panel-total,
.panel-count {
  color: #e35b2f;
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
}

.panel-count {
  color: #4d5c6c;
}

.trend-chart {
  height: 300px;
  display: grid;
  grid-template-columns: repeat(7, minmax(36px, 1fr));
  align-items: end;
  gap: 12px;
  padding-top: 26px;
}

.trend-column {
  min-width: 0;
  height: 100%;
  display: grid;
  grid-template-rows: 20px minmax(0, 1fr) 22px;
  align-items: end;
  justify-items: center;
  gap: 6px;
}

.trend-value {
  color: #7d8996;
  font-size: 11px;
  white-space: nowrap;
}

.bar-track {
  width: min(42px, 72%);
  height: 100%;
  display: flex;
  align-items: flex-end;
  border-radius: 7px 7px 3px 3px;
  background: #f3f5f7;
  overflow: hidden;
}

.bar-fill {
  width: 100%;
  min-height: 0;
  border-radius: 7px 7px 3px 3px;
  background: linear-gradient(180deg, #ff8a5c, #f05a2b);
  transition: height 0.3s ease;
}

.trend-date {
  color: #85919e;
  font-size: 12px;
}

.status-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.status-copy {
  margin-bottom: 8px;
  color: #5d6b7a;
  font-size: 13px;
}

.status-name {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.status-copy strong {
  color: #2d3a48;
  font-size: 14px;
}

.status-dot {
  width: 8px;
  height: 8px;
  display: inline-block;
  border-radius: 50%;
}

.progress-track {
  height: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: #f0f2f5;
}

.progress-fill {
  height: 100%;
  border-radius: inherit;
  transition: width 0.3s ease;
}

.status-0,
.progress-fill.status-0 {
  color: #d99b2b;
  background: #e9ad3d;
}

.status-1,
.progress-fill.status-1 {
  color: #3978d6;
  background: #4f8ce0;
}

.status-2,
.progress-fill.status-2 {
  color: #7357d9;
  background: #846be0;
}

.status-3,
.progress-fill.status-3 {
  color: #2f9e62;
  background: #45b978;
}

.status-4,
.progress-fill.status-4 {
  color: #8a96a3;
  background: #aeb7c1;
}

@media (max-width: 1100px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .panel {
    padding: 16px;
  }

  .trend-chart {
    height: 240px;
    gap: 6px;
  }

  .trend-value {
    display: none;
  }

  .trend-column {
    grid-template-rows: minmax(0, 1fr) 22px;
  }
}
</style>
