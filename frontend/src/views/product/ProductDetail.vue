<template>
  <div class="product-detail-page">
    <el-breadcrumb separator="/" class="breadcrumb">
      <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>商品详情</el-breadcrumb-item>
    </el-breadcrumb>

    <div v-if="loading" class="skeleton-wrapper">
      <el-skeleton :rows="3" animated />
      <el-skeleton :rows="5" animated class="mt-4" />
    </div>

    <div v-else-if="error" class="error-wrapper">
      <el-empty description="商品信息加载失败">
        <el-button type="primary" @click="fetchProduct">重新加载</el-button>
      </el-empty>
    </div>

    <template v-else-if="product && product.id">
      <div v-if="!userStore.isLoggedIn" class="guest-notice">
        <div class="guest-notice-copy">
          <el-icon><View /></el-icon>
          <span>当前为游客浏览模式，登录后可使用收藏、购物车和购买功能。</span>
        </div>
        <div class="guest-notice-actions">
          <el-button link type="primary" @click="$router.push('/login')">登录</el-button>
          <el-button link type="primary" @click="$router.push('/register')">注册</el-button>
        </div>
      </div>

      <div class="actions-bar">
        <el-button text @click="toggleFavorite">
          <el-icon><Star /></el-icon>
          {{ isFavorite ? '已收藏' : '收藏' }}
        </el-button>
      </div>

      <el-row :gutter="30">
        <el-col :xs="24" :sm="24" :md="14" :lg="12">
          <div class="product-images">
            <div class="main-image-wrapper">
              <el-image
                :src="currentImage"
                fit="cover"
                class="main-image"
                :preview-src-list="images"
              >
                <template #error>
                  <div class="image-placeholder"><el-icon><Picture /></el-icon></div>
                </template>
              </el-image>
            </div>
            <el-carousel
              v-if="images.length > 1"
              height="80px"
              indicator-position="none"
              arrow="always"
              class="thumb-carousel"
              @change="currentImageIndex = $event"
            >
              <el-carousel-item v-for="(img, index) in images" :key="index">
                <img
                  :src="img"
                  class="thumb-img"
                  :class="{ active: index === currentImageIndex }"
                  alt="商品图片"
                  @click="currentImageIndex = index"
                >
              </el-carousel-item>
            </el-carousel>
          </div>
        </el-col>

        <el-col :xs="24" :sm="24" :md="10" :lg="12">
          <div class="product-info-panel">
            <h1 class="product-title">{{ product.name }}</h1>
            <p class="product-subtitle">{{ product.subtitle }}</p>

            <div class="price-section">
              <div class="price-row">
                <span class="sale-price">¥{{ formatPrice(product.salePrice || product.price) }}</span>
                <span v-if="product.salePrice && product.salePrice !== product.price" class="original-price">¥{{ product.price }}</span>
                <span v-if="discountPercent > 0" class="discount-tag">已省{{ discountPercent }}%</span>
              </div>
              <div class="promo-tags" v-if="product.promoTags && product.promoTags.length">
                <el-tag
                  v-for="tag in product.promoTags"
                  :key="tag"
                  type="danger"
                  size="small"
                  class="promo-tag"
                >
                  {{ tag }}
                </el-tag>
              </div>
            </div>

            <el-divider />

            <div class="stock-info">
              <span :class="product.stock > 0 ? 'stock-ok' : 'stock-out'">
                {{ product.stock > 0 ? `库存：${product.stock}件` : '已售罄' }}
              </span>
              <span>销量：{{ product.sales }}件</span>
            </div>
            <el-divider />

            <el-form label-width="80px" class="sku-form">
              <el-form-item label="规格">
                <el-radio-group v-if="skus.length > 0" v-model="selectedSku">
                  <el-radio-button
                    v-for="sku in skus"
                    :key="sku.id"
                    :label="sku"
                    :disabled="sku.stock <= 0"
                    class="sku-option"
                  >
                    <span>{{ sku.specDesc }}</span>
                    <span class="sku-price">¥{{ sku.price }}</span>
                    <span v-if="sku.stock <= 0" class="sku-empty">缺货</span>
                  </el-radio-button>
                </el-radio-group>
                <span v-else class="default-spec">默认规格</span>
              </el-form-item>
              <el-form-item label="数量">
                <el-input-number
                  v-model="quantity"
                  :min="1"
                  :max="maxQuantity"
                  :disabled="maxQuantity <= 0"
                  controls-position="right"
                  class="quantity-selector"
                />
                <span v-if="maxQuantity > 0" class="sku-stock">
                  剩余 {{ maxQuantity }} 件
                </span>
              </el-form-item>
            </el-form>

            <div class="action-buttons">
              <el-button
                type="primary"
                size="large"
                class="buy-btn"
                :loading="addingToCart"
                :disabled="!canAddToCart || addingToCart"
                @click="handleAddToCart"
              >
                加入购物车
              </el-button>
              <el-button
                type="danger"
                size="large"
                class="buy-btn buy-now"
                :disabled="!canAddToCart || buyingNow"
                @click="handleBuyNow"
              >
                立即购买
              </el-button>
            </div>

            <el-divider />

            <div class="product-desc">
              <h3>商品详情</h3>
              <div v-html="product.description"></div>
            </div>
          </div>
        </el-col>
      </el-row>
    </template>

    <div class="sticky-bar" v-if="product && product.id && !loading && !error">
      <div class="sticky-content">
        <div class="sticky-price">
          <span class="current-price">¥{{ formatPrice(product.salePrice || product.price) }}</span>
          <span v-if="selectedSku" class="sku-text">{{ selectedSku.specDesc }}</span>
          <span v-else class="sku-text">默认规格</span>
        </div>
        <div class="sticky-actions">
          <el-button
            type="primary"
            size="small"
            :loading="addingToCart"
            :disabled="!canAddToCart || addingToCart"
            @click="handleAddToCart"
          >
            加入购物车
          </el-button>
          <el-button
            type="danger"
            size="small"
            :disabled="!canAddToCart || buyingNow"
            @click="handleBuyNow"
          >
            立即购买
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProductDetail } from '@/api/productApi'
import { addCollect, removeCollect, getCollectList } from '@/api/collectApi'
import { useCartStore } from '@/stores/cartStore'
import { useCheckoutStore } from '@/stores/checkoutStore'
import { useUserStore } from '@/stores/userStore'
import { ensureLoggedIn } from '@/utils/auth'
import { ElMessage } from 'element-plus'
import { Picture, Star, View } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()
const checkoutStore = useCheckoutStore()
const userStore = useUserStore()

const product = ref<any>({})
const skus = ref<any[]>([])
const selectedSku = ref<any>(null)
const quantity = ref(1)
const images = ref<string[]>([])
const currentImageIndex = ref(0)
const loading = ref(true)
const error = ref(false)
const isFavorite = ref(false)
const collectId = ref<number | null>(null)
const addingToCart = ref(false)
const buyingNow = ref(false)

const currentImage = computed(() => images.value[currentImageIndex.value] || '')

const discountPercent = computed(() => {
  const sale = product.value.salePrice || product.value.price
  const original = product.value.price
  if (original && sale && sale < original) {
    return Math.round((1 - sale / original) * 100)
  }
  return 0
})

const maxQuantity = computed(() => {
  const stock = selectedSku.value ? selectedSku.value.stock : product.value.stock
  return Math.max(0, Number(stock) || 0)
})

const canAddToCart = computed(() => {
  if (!product.value.id || maxQuantity.value <= 0) return false
  if (skus.value.length > 0 && !selectedSku.value) return false
  return true
})

const formatPrice = (price: number | string | undefined) => {
  return Number(price || 0).toFixed(2)
}

const parseImages = (value: any): string[] => {
  if (Array.isArray(value)) return value.filter(Boolean)
  if (typeof value !== 'string' || !value.trim()) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.filter(Boolean) : []
  } catch {
    return [value]
  }
}

const syncFavoriteStatus = async () => {
  if (!userStore.isLoggedIn) {
    isFavorite.value = false
    collectId.value = null
    return
  }
  try {
    const res = await getCollectList()
    const item = (res.data || []).find((c: any) => c.productId === product.value.id)
    isFavorite.value = !!item
    collectId.value = item?.id || null
  } catch {
    isFavorite.value = false
    collectId.value = null
  }
}

const toggleFavorite = async () => {
  if (!product.value.id) return
  if (!(await ensureLoggedIn('登录后即可收藏商品'))) return
  try {
    if (isFavorite.value && collectId.value) {
      await removeCollect(collectId.value)
      isFavorite.value = false
      collectId.value = null
      ElMessage.success('已取消收藏')
    } else {
      const res = await addCollect({ productId: product.value.id })
      isFavorite.value = true
      collectId.value = res.data || null
      if (!collectId.value) {
        await syncFavoriteStatus()
      }
      ElMessage.success('已收藏')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

const fetchProduct = async () => {
  loading.value = true
  error.value = false
  try {
    const id = Number(route.params.id)
    const res = await getProductDetail(id)
    if (!res.data || !res.data.id) {
      throw new Error('商品不存在')
    }
    product.value = res.data
    skus.value = res.data.skus || []
    images.value = parseImages(res.data.images)
    if (skus.value.length > 0) {
      const availableSku = skus.value.find(s => s.stock > 0) || skus.value[0]
      selectedSku.value = availableSku
    }
    quantity.value = 1
    currentImageIndex.value = 0
    await syncFavoriteStatus()
  } catch (e) {
    error.value = true
    ElMessage.error('加载商品信息失败')
  } finally {
    loading.value = false
  }
}

const handleAddToCart = async () => {
  if (!(await ensureLoggedIn('登录后即可将商品加入购物车'))) return
  if (skus.value.length > 0 && !selectedSku.value) {
    ElMessage.warning('请选择规格')
    return
  }
  if (!canAddToCart.value) {
    ElMessage.warning('当前商品库存不足')
    return
  }
  addingToCart.value = true
  try {
    await cartStore.addItem({
      productId: product.value.id,
      skuId: selectedSku.value?.id,
      quantity: quantity.value
    })
    ElMessage.success('已加入购物车')
  } catch (e: any) {
    ElMessage.error(e.message || '加入购物车失败')
  } finally {
    addingToCart.value = false
  }
}

const handleBuyNow = async () => {
  if (!(await ensureLoggedIn('登录后即可购买商品'))) return
  if (!canAddToCart.value) {
    ElMessage.warning('当前商品库存不足')
    return
  }
  buyingNow.value = true
  checkoutStore.setDirectItem({
    id: `direct-${product.value.id}-${selectedSku.value?.id || 'default'}`,
    productId: product.value.id,
    skuId: selectedSku.value?.id,
    productName: product.value.name,
    skuDesc: selectedSku.value?.specDesc || '默认规格',
    price: Number(selectedSku.value?.price ?? product.value.salePrice ?? product.value.price),
    quantity: quantity.value,
    stock: maxQuantity.value,
    image: selectedSku.value?.image || currentImage.value
  })
  router.push({ path: '/order/confirm', query: { direct: '1' } })
  buyingNow.value = false
}

onMounted(() => {
  fetchProduct()
})
</script>

<style scoped>
.product-detail-page {
  padding-bottom: 80px;
}

.breadcrumb {
  padding: 16px 0;
}

.guest-notice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding: 12px 16px;
  border: 1px solid #d9ecff;
  border-radius: 8px;
  background: #f4f9ff;
  color: #4b6b8f;
  font-size: 14px;
}

.guest-notice-copy,
.guest-notice-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.skeleton-wrapper {
  padding: 20px;
}

.error-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}

.actions-bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 10px;
}

.product-images {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  height: 100%;
}

.main-image-wrapper {
  width: 100%;
  height: 420px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8f8f8;
  border-radius: 8px;
  overflow: hidden;
}

.main-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-placeholder {
  width: 100%;
  height: 100%;
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  color: #c0c4cc;
  font-size: 48px;
}

.thumb-carousel {
  margin-top: 12px;
}

.thumb-img {
  width: 100%;
  height: 60px;
  object-fit: cover;
  border-radius: 4px;
  opacity: 0.6;
  transition: opacity 0.3s;
  cursor: pointer;
}

.thumb-img:hover,
.thumb-img.active {
  opacity: 1;
}

.product-info-panel {
  background: #fff;
  padding: 24px;
  border-radius: 8px;
  height: 100%;
}

.product-title {
  font-size: 22px;
  font-weight: 600;
  margin-bottom: 8px;
  color: #303133;
}

.product-subtitle {
  color: #909399;
  font-size: 14px;
  margin-bottom: 16px;
}

.price-section {
  margin-bottom: 4px;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}

.sale-price {
  color: #e4393c;
  font-size: 28px;
  font-weight: bold;
}

.original-price {
  color: #999;
  font-size: 16px;
  text-decoration: line-through;
}

.discount-tag {
  background: linear-gradient(135deg, #ff6b6b, #ee5a5a);
  color: #fff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: bold;
}

.promo-tags {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.promo-tag {
  margin-right: 0;
}

.stock-info {
  display: flex;
  justify-content: space-between;
  color: #666;
  font-size: 14px;
}

.stock-ok {
  color: #67c23a;
}

.stock-out {
  color: #e4393c;
}

.sku-form {
  margin-bottom: 16px;
}

.sku-option {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}

.sku-price {
  color: #e4393c;
  font-size: 12px;
}

.sku-empty {
  color: #c0c4cc;
  font-size: 11px;
  margin-left: 4px;
}

.quantity-selector {
  width: 120px;
}

.default-spec {
  color: #606266;
  font-size: 14px;
}

.sku-stock {
  color: #909399;
  font-size: 12px;
  margin-left: 12px;
}

.action-buttons {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.buy-btn {
  flex: 1;
}

.buy-now {
  background: linear-gradient(135deg, #f56c6c, #e6a23c);
  border-color: #f56c6c;
}

.product-desc {
  margin-top: 16px;
}

.product-desc h3 {
  font-size: 16px;
  margin-bottom: 12px;
}

.product-desc :deep(div) {
  line-height: 1.8;
}

.sticky-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-top: 1px solid #ebeef5;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.1);
  z-index: 1000;
  padding: 10px 16px;
}

.sticky-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1200px;
  margin: 0 auto;
}

.sticky-price {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.current-price {
  color: #e4393c;
  font-size: 20px;
  font-weight: bold;
}

.sku-text {
  color: #909399;
  font-size: 12px;
}

.sticky-actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 768px) {
  .guest-notice {
    align-items: flex-start;
    flex-direction: column;
  }

  .main-image-wrapper {
    height: 300px;
  }

  .product-title {
    font-size: 18px;
  }

  .sale-price {
    font-size: 22px;
  }

  .action-buttons {
    flex-direction: column;
  }

  .sticky-bar .sticky-actions {
    flex-direction: column;
  }
}
</style>




