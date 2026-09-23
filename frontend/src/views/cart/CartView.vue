<template>
  <div class="cart-page">
    <div class="cart-header">
      <div>
        <h2 class="page-title">购物车</h2>
        <p class="page-subtitle">已加入购物车的商品会保存在当前账号下</p>
      </div>
      <el-button v-if="cartStore.cartList.length > 0" type="danger" text @click="handleClear">
        清空购物车
      </el-button>
    </div>

    <el-card class="cart-card" shadow="never">
      <el-table
        ref="cartTable"
        v-loading="cartStore.loading"
        :data="cartStore.cartList"
        :row-key="(row: any) => row.id"
        style="width: 100%"
        empty-text="购物车暂无商品"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" :selectable="isSelectable" reserve-selection />
        <el-table-column label="商品信息" min-width="340">
          <template #default="{ row }">
            <div class="cart-item">
              <el-image :src="row.image || ''" fit="cover" class="cart-item-img">
                <template #error>
                  <div class="image-placeholder"><el-icon><Picture /></el-icon></div>
                </template>
              </el-image>
              <div class="cart-item-info">
                <p class="cart-item-name" @click="$router.push(`/product/${row.productId}`)">
                  {{ row.productName || row.name }}
                </p>
                <p class="cart-item-spec">{{ row.skuDesc || '默认规格' }}</p>
                <p class="cart-item-price">¥{{ formatPrice(row.price) }}</p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="库存" width="100">
          <template #default="{ row }">
            <span :class="{ 'stock-warning': row.stock <= row.quantity }">{{ row.stock }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="170">
          <template #default="{ row }">
            <el-input-number
              v-model="row.quantity"
              :min="1"
              :max="row.stock || 999"
              controls-position="right"
              @change="onQuantityChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="小计" width="130">
          <template #default="{ row }">
            <span class="subtotal">¥{{ formatPrice(Number(row.price) * Number(row.quantity)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" text @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!cartStore.loading && cartStore.cartList.length === 0" description="购物车为空">
        <el-button type="primary" @click="$router.push('/home')">去逛逛</el-button>
      </el-empty>
    </el-card>

    <div class="cart-footer" v-if="cartStore.cartList.length > 0">
      <div class="total-section">
        <span>已选 {{ cartStore.selectedCount }} 件商品</span>
        <span class="total-price">合计：¥{{ formatPrice(cartStore.selectedPrice) }}</span>
      </div>
      <el-button
        type="primary"
        size="large"
        :disabled="cartStore.selectedItems.length === 0"
        @click="toCheckout"
      >
        去结算
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { useCartStore } from '@/stores/cartStore'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'

const cartStore = useCartStore()
const router = useRouter()
const cartTable = ref<any>(null)

const formatPrice = (price: number) => {
  return Number(price || 0).toFixed(2)
}

const onQuantityChange = async (row: any) => {
  try {
    await cartStore.updateQuantity(row.id, row.quantity)
  } catch (e: any) {
    ElMessage.error(e.message || '修改数量失败')
    await cartStore.loadCart()
  }
}

const isSelectable = (row: any) => Number(row.stock || 0) > 0

const handleSelectionChange = (rows: any[]) => {
  cartStore.setSelectedIds(rows.map((row) => row.id))
}

watch(
  () => cartStore.cartList,
  async () => {
    await nextTick()
    cartStore.cartList.forEach((row) => {
      cartTable.value?.toggleRowSelection(row, cartStore.selectedIds.includes(row.id))
    })
  },
  { deep: true }
)

const handleRemove = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定要删除“${row.productName || row.name}”吗？`, '提示', {
      type: 'warning'
    })
    await cartStore.removeItem(row.id)
    ElMessage.success('已移除商品')
  } catch {}
}

const handleClear = async () => {
  try {
    await ElMessageBox.confirm('确定要清空购物车吗？', '提示', { type: 'warning' })
    await cartStore.clearCart()
    ElMessage.success('购物车已清空')
  } catch {}
}

const toCheckout = () => {
  if (cartStore.selectedItems.length === 0) {
    ElMessage.warning('请选择要结算的商品')
    return
  }
  router.push('/order/confirm')
}

onMounted(() => {
  cartStore.loadCart()
})
</script>

<style scoped>
.cart-page {
  padding: 4px 0 24px;
}

.cart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  font-size: 24px;
  margin-bottom: 6px;
  color: #303133;
}

.page-subtitle {
  color: #909399;
  font-size: 13px;
}

.cart-card {
  border-radius: 10px;
}

.cart-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 4px 0;
}

.cart-item-img {
  width: 84px;
  height: 84px;
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
  background: #f5f7fa;
  color: #c0c4cc;
}

.cart-item-name {
  font-weight: 600;
  margin-bottom: 6px;
  color: #303133;
  cursor: pointer;
}

.cart-item-name:hover {
  color: #e4393c;
}

.cart-item-spec {
  color: #999;
  font-size: 12px;
  margin-bottom: 6px;
}

.cart-item-price {
  color: #e4393c;
}

.subtotal {
  color: #e4393c;
  font-weight: 600;
}

.stock-warning {
  color: #e6a23c;
}

.cart-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20px;
  padding: 20px 24px;
  background: #fff;
  border-radius: 10px;
  position: sticky;
  bottom: 16px;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.08);
}

.total-section {
  display: flex;
  align-items: baseline;
  gap: 18px;
  color: #606266;
}

.total-price {
  font-size: 22px;
  color: #e4393c;
  font-weight: bold;
}

@media (max-width: 768px) {
  .cart-header,
  .cart-footer {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .total-section {
    justify-content: space-between;
  }
}
</style>
