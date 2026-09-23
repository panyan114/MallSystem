<template>
  <div class="order-confirm-page" v-loading="loading">
    <el-row :gutter="20">
      <el-col :xs="24" :md="14">
        <el-card class="section-card">
          <template #header>收货信息</template>
          <el-form ref="formRef" :model="addressForm" :rules="rules" label-width="80px">
            <el-form-item label="收货人" prop="receiver">
              <el-input v-model="addressForm.receiver" />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="addressForm.phone" />
            </el-form-item>
            <el-form-item label="地址" prop="address">
              <el-input v-model="addressForm.address" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>
        </el-card>
        <el-card class="section-card" style="margin-top: 20px">
          <template #header>商品清单</template>
          <div v-for="item in checkoutItems" :key="item.id" class="order-item">
            <span>{{ item.productName }} × {{ item.quantity }}</span>
            <span class="item-price">¥{{ formatPrice(item.price * item.quantity) }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="10">
        <el-card class="summary-card">
          <template #header>订单详情</template>
          <div class="summary-row">
            <span>商品总额</span>
            <span>¥{{ formatPrice(checkoutTotal) }}</span>
          </div>
          <div class="summary-row">
            <span>运费</span>
            <span>¥0.00</span>
          </div>
          <el-divider class="summary-divider" />
          <div class="summary-row total">
            <span>应付总额</span>
            <span>¥{{ formatPrice(checkoutTotal) }}</span>
          </div>
          <el-button
            type="primary"
            class="submit-btn"
            :loading="submitting"
            :disabled="checkoutItems.length === 0"
            @click="submitOrder"
          >
            提交订单
          </el-button>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useCartStore } from '@/stores/cartStore'
import { useCheckoutStore } from '@/stores/checkoutStore'
import { createOrder } from '@/api/orderApi'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()
const checkoutStore = useCheckoutStore()
const formRef = ref<FormInstance>()
const addressForm = ref({ receiver: '', phone: '', address: '' })
const submitting = ref(false)
const loading = ref(false)

const rules: FormRules = {
  receiver: [{ required: true, message: '请填写收货人', trigger: 'blur' }],
  phone: [
    { required: true, message: '请填写手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  address: [{ required: true, message: '请填写收货地址', trigger: 'blur' }]
}

const isDirectBuy = computed(() => route.query.direct === '1' && !!checkoutStore.directItem)
const checkoutItems = computed(() => {
  if (isDirectBuy.value && checkoutStore.directItem) {
    return [checkoutStore.directItem]
  }
  return cartStore.selectedItems
})
const checkoutTotal = computed(() =>
  checkoutItems.value.reduce((sum, item) => sum + Number(item.price || 0) * Number(item.quantity || 0), 0)
)

const formatPrice = (price: number) => Number(price || 0).toFixed(2)

const submitOrder = async () => {
  if (!formRef.value || checkoutItems.value.length === 0) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const res = await createOrder({
      items: checkoutItems.value.map(item => ({
        productId: item.productId,
        skuId: item.skuId,
        quantity: item.quantity
      })),
      address: addressForm.value.address,
      receiver: addressForm.value.receiver,
      phone: addressForm.value.phone,
      clearCart: !isDirectBuy.value
    })
    if (isDirectBuy.value) {
      checkoutStore.clearDirectItem()
    } else {
      await cartStore.loadCart()
    }
    ElMessage.success('订单提交成功')
    router.replace(`/order/detail/${res.data.id}`)
  } catch (e: any) {
    ElMessage.error(e.message || '订单提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    if (route.query.direct === '1' && !checkoutStore.directItem) {
      ElMessage.warning('立即购买信息已失效，请重新选择商品')
      router.replace('/cart')
      return
    }
    if (!isDirectBuy.value) {
      await cartStore.loadCart()
      if (cartStore.selectedItems.length === 0) {
        ElMessage.warning('请先选择要结算的商品')
        router.replace('/cart')
      }
    }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.order-confirm-page {
  padding: 20px;
}
.section-card {
  margin-bottom: 20px;
}
.order-item {
  display: flex;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}
.item-price {
  color: #e4393c;
}
.summary-card {
  position: sticky;
  top: 20px;
}
.summary-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
}
.summary-row.total {
  font-size: 20px;
  font-weight: bold;
}
.summary-divider {
  margin: 12px 0;
}
.submit-btn {
  width: 100%;
  margin-top: 16px;
  height: 48px;
  font-size: 16px;
}

@media (max-width: 768px) {
  .order-confirm-page {
    padding: 0;
  }

  .summary-card {
    position: static;
    margin-top: 20px;
  }
}
</style>

