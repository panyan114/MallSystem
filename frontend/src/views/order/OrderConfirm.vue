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
        <el-card class="section-card" style="margin-top: 20px">
          <template #header>优惠券</template>
          <el-select
            v-model="selectedUserCouponId"
            clearable
            placeholder="不使用优惠券"
            style="width: 100%"
            :no-data-text="couponOptions.length === 0 ? '暂无可用优惠券' : '没有满足条件的优惠券'"
          >
            <el-option
              v-for="coupon in couponOptions"
              :key="coupon.id"
              :label="couponLabel(coupon)"
              :value="coupon.id"
              :disabled="!couponUsable(coupon)"
            />
          </el-select>
          <p v-if="selectedCoupon" class="coupon-hint">
            已选「{{ selectedCoupon.name }}」，本单可减 ¥{{ formatPrice(discountAmount) }}
          </p>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="10">
        <el-card class="summary-card">
          <template #header>订单详情</template>
          <div class="summary-row">
            <span>商品总额</span>
            <span>¥{{ formatPrice(checkoutTotal) }}</span>
          </div>
          <div v-if="discountAmount > 0" class="summary-row discount">
            <span>优惠券</span>
            <span>-¥{{ formatPrice(discountAmount) }}</span>
          </div>
          <div class="summary-row">
            <span>运费</span>
            <span>¥0.00</span>
          </div>
          <el-divider class="summary-divider" />
          <div class="summary-row total">
            <span>应付总额</span>
            <span>¥{{ formatPrice(payableAmount) }}</span>
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
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useCartStore } from '@/stores/cartStore'
import { useCheckoutStore } from '@/stores/checkoutStore'
import { createOrder } from '@/api/orderApi'
import { getMyCoupons } from '@/api/couponApi'
import { COUPON_TYPE_DISCOUNT, type UserCoupon } from '@/types'
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

// ---- 优惠券 ----
//
// 注意 checkoutTotal 是本地按缓存价格算的，不是服务端报价。价格在加购后变动时，
// 这里的预览会和服务端最终算出的金额有出入——这是既有问题（原来的「应付总额」就有），
// 不在本次范围内。订单详情页展示的是服务端权威值。
const couponOptions = ref<UserCoupon[]>([])
const selectedUserCouponId = ref<number | null>(null)

const selectedCoupon = computed(
  () => couponOptions.value.find(c => c.id === selectedUserCouponId.value) || null
)

/**
 * 预览金额全部用**整数分**计算，以和服务端的 BigDecimal HALF_UP 逐分对齐。
 * 用浮点算会出现 Math.round(7790 * 0.9) 之外的结果，预览就会和实付差一分钱。
 */
const totalCents = computed(() => Math.round(Number(checkoutTotal.value || 0) * 100))

const payableCents = (coupon: UserCoupon) => {
  const total = totalCents.value
  if (coupon.type === COUPON_TYPE_DISCOUNT) {
    // 与后端一致：先算应付，再倒推优惠。
    // 折扣率也必须换算成整数再乘（0.7 → 70）。JS 双精度下 45 * 0.7 = 31.499999999999996，
    // Math.round 得 31 分；后端 BigDecimal 算的是 0.3150 → HALF_UP → 32 分，
    // 预览就比实付少一分钱。券的 7 折档在 ¥0.01~¥2000 区间内有 4681 个金额会踩到。
    // 后端 discount_value 是 decimal(10,2)，所以 ×100 取整无损。
    const rateCents = Math.round(Number(coupon.discountValue) * 100)
    return Math.round((total * rateCents) / 100)
  }
  const off = Math.min(Math.round(Number(coupon.discountValue || 0) * 100), total)
  return Math.max(total - off, 0)
}

const discountAmount = computed(() => {
  if (!selectedCoupon.value) return 0
  return (totalCents.value - payableCents(selectedCoupon.value)) / 100
})

const payableAmount = computed(() => {
  if (!selectedCoupon.value) return checkoutTotal.value
  return payableCents(selectedCoupon.value) / 100
})

const couponUsable = (coupon: UserCoupon) =>
  totalCents.value >= Math.round(Number(coupon.minAmount || 0) * 100)

const couponLabel = (coupon: UserCoupon) => {
  const value = coupon.type === COUPON_TYPE_DISCOUNT
    ? `${(Number(coupon.discountValue) * 10).toFixed(1).replace(/\.0$/, '')}折`
    : `满¥${formatPrice(coupon.minAmount)}减¥${formatPrice(coupon.discountValue)}`
  return couponUsable(coupon) ? `${coupon.name}（${value}）` : `${coupon.name}（${value}）· 未满门槛`
}

const loadCoupons = async () => {
  try {
    const res = await getMyCoupons()
    // 只留未使用的。已使用/已过期的不该出现在结算页。
    couponOptions.value = (res.data?.list || []).filter((c: UserCoupon) => c.status === 0)
  } catch {
    // 领券失败不该挡住下单，静默降级成「没有可用优惠券」
    couponOptions.value = []
  }
}

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
      // 提交的是用户券 id（t_user_coupon.id），不是券模板 id
      userCouponId: selectedUserCouponId.value || undefined,
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

// 选中的券若因金额变化不再满足门槛，主动清掉选择——否则会带着一个用不了的券去提交，
// 只能等服务端报「未满 X 元」，体验很差。
watch([totalCents, couponOptions], () => {
  if (selectedCoupon.value && !couponUsable(selectedCoupon.value)) {
    ElMessage.warning('已选优惠券不再满足使用门槛，已取消选择')
    selectedUserCouponId.value = null
  }
})

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
        return
      }
    }
    await loadCoupons()
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
.summary-row.discount {
  color: #e4393c;
}
.coupon-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: #e4393c;
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

