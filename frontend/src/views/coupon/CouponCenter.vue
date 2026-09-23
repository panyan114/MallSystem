<template>
  <div class="coupon-page">
    <div class="page-header">
      <div>
        <h2>我的优惠券</h2>
        <p class="page-subtitle">领取优惠券，下单结算时自动抵扣</p>
      </div>
      <el-button @click="loadAll">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <el-card class="toolbar-card" shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane name="available">
          <template #label>
            可领取
            <el-badge v-if="claimableList.length > 0" :value="claimableList.length" class="tab-badge" />
          </template>
        </el-tab-pane>
        <el-tab-pane label="未使用" name="unused" />
        <el-tab-pane label="已使用" name="used" />
        <el-tab-pane label="已过期" name="expired" />
      </el-tabs>
    </el-card>

    <div v-loading="loading" class="coupon-list">
      <el-empty v-if="currentList.length === 0" :description="emptyText" />

      <div v-for="item in currentList" :key="item.key" class="coupon-card" :class="{ disabled: item.dimmed }">
        <div class="coupon-value">
          <span class="amount">{{ item.valueText }}</span>
          <span class="threshold">{{ item.thresholdText }}</span>
        </div>
        <div class="coupon-info">
          <p class="coupon-name">{{ item.name }}</p>
          <p class="coupon-time">{{ item.timeText }}</p>
        </div>
        <div class="coupon-action">
          <el-button
            v-if="activeTab === 'available'"
            type="primary"
            size="small"
            :disabled="item.received"
            :loading="receivingId === item.couponId"
            @click="handleReceive(item.couponId)"
          >
            {{ item.received ? '已领取' : '立即领取' }}
          </el-button>
          <el-tag v-else :type="item.tagType" size="small">{{ item.tagText }}</el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getAvailableCoupons, getMyCoupons, receiveCoupon } from '@/api/couponApi'
import { COUPON_TYPE_DISCOUNT, type Coupon, type UserCoupon } from '@/types'

/** 与后端 t_user_coupon.status 对齐。2（已过期）由后端读时派生，库里只有 0 和 1。 */
const STATUS_UNUSED = 0
const STATUS_USED = 1
const STATUS_EXPIRED = 2

const activeTab = ref('available')
const loading = ref(false)
const receivingId = ref<number | null>(null)
const availableList = ref<Coupon[]>([])
const myList = ref<UserCoupon[]>([])

/** 可领取 tab 里排除已领完的——那些按钮点了也没用 */
const claimableList = computed(() =>
  availableList.value.filter((c) => c.remainCount > 0 || c.received)
)

interface DisplayItem {
  key: string
  couponId: number
  name: string
  valueText: string
  thresholdText: string
  timeText: string
  received: boolean
  dimmed: boolean
  tagText: string
  tagType: 'success' | 'info' | 'warning'
}

const currentList = computed<DisplayItem[]>(() => {
  if (activeTab.value === 'available') {
    return availableList.value
      .filter((c) => c.remainCount > 0 || c.received)
      .map((c) => ({
        key: `a-${c.id}`,
        couponId: c.id,
        name: c.name,
        valueText: valueTextOf(c),
        thresholdText: thresholdTextOf(c),
        timeText: `剩余 ${c.remainCount} 张 · 有效期至 ${formatTime(c.endTime)}`,
        received: c.received,
        dimmed: c.received,
        tagText: '',
        tagType: 'info' as const
      }))
  }

  const wanted = activeTab.value === 'unused' ? STATUS_UNUSED
    : activeTab.value === 'used' ? STATUS_USED
      : STATUS_EXPIRED

  return myList.value
    .filter((c) => c.status === wanted)
    .map((c) => ({
      key: `m-${c.id}`,
      couponId: c.id,
      name: c.name,
      valueText: valueTextOf(c),
      thresholdText: thresholdTextOf(c),
      timeText: c.status === STATUS_USED
        ? `已于 ${formatTime(c.useTime)} 使用`
        : `有效期至 ${formatTime(c.endTime)}`,
      received: false,
      dimmed: c.status !== STATUS_UNUSED,
      tagText: c.status === STATUS_UNUSED ? '未使用' : c.status === STATUS_USED ? '已使用' : '已过期',
      tagType: (c.status === STATUS_UNUSED ? 'success' : c.status === STATUS_USED ? 'info' : 'warning') as
        'success' | 'info' | 'warning'
    }))
})

const emptyText = computed(() => {
  switch (activeTab.value) {
    case 'available':
      return '暂无可领取的优惠券'
    case 'unused':
      return '还没有可用的优惠券，去「可领取」看看'
    case 'used':
      return '还没有使用过优惠券'
    default:
      return '没有已过期的优惠券'
  }
})

const valueTextOf = (coupon: { type: number; discountValue: number }) =>
  coupon.type === COUPON_TYPE_DISCOUNT
    // 0.90 → 9折，0.85 → 8.5折
    ? `${(Number(coupon.discountValue) * 10).toFixed(1).replace(/\.0$/, '')}折`
    : `¥${Number(coupon.discountValue || 0).toFixed(2)}`

const thresholdTextOf = (coupon: { minAmount: number }) =>
  Number(coupon.minAmount) > 0 ? `满 ¥${Number(coupon.minAmount).toFixed(2)} 可用` : '无门槛'

const formatTime = (value: any) => {
  if (!value) return '-'
  if (Array.isArray(value)) {
    const [year, month, day, hour, minute, second] = value
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${year}-${pad(month)}-${pad(day)} ${pad(hour)}:${pad(minute)}:${pad(second)}`
  }
  return String(value).replace('T', ' ').slice(0, 19)
}

const loadAll = async () => {
  loading.value = true
  try {
    const [available, mine] = await Promise.all([getAvailableCoupons(), getMyCoupons()])
    availableList.value = available.data?.list || []
    myList.value = mine.data?.list || []
  } finally {
    loading.value = false
  }
}

const handleReceive = async (couponId: number) => {
  receivingId.value = couponId
  try {
    await receiveCoupon(couponId)
    ElMessage.success('领取成功')
  } catch (e: any) {
    // 「已领取」这类业务失败也走这里。刷新一下让按钮状态跟上，用户不必手动刷新。
    ElMessage.warning(e.message || '领取失败')
  } finally {
    receivingId.value = null
    await loadAll()
  }
}

onMounted(loadAll)
</script>

<style scoped>
.coupon-page {
  max-width: 1000px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0 0 6px;
  font-size: 22px;
}

.page-subtitle {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.toolbar-card {
  margin-bottom: 16px;
}

.toolbar-card :deep(.el-card__body) {
  padding-bottom: 0;
}

.tab-badge {
  margin-left: 4px;
  vertical-align: middle;
}

.coupon-list {
  min-height: 200px;
}

.coupon-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 18px 20px;
  margin-bottom: 12px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  transition: box-shadow 0.2s;
}

.coupon-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.coupon-card.disabled {
  opacity: 0.6;
}

.coupon-value {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 130px;
  padding: 10px 16px;
  border-right: 1px dashed #ebeef5;
}

.coupon-value .amount {
  font-size: 26px;
  font-weight: bold;
  color: #e4393c;
  line-height: 1.2;
}

.coupon-value .threshold {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.coupon-info {
  flex: 1;
  min-width: 0;
}

.coupon-name {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 500;
}

.coupon-time {
  margin: 0;
  font-size: 12px;
  color: #909399;
}

.coupon-action {
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: 12px;
  }

  .coupon-card {
    flex-wrap: wrap;
    gap: 12px;
  }

  .coupon-value {
    border-right: none;
    border-bottom: 1px dashed #ebeef5;
    width: 100%;
  }
}
</style>
