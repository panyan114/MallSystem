<template>
  <div class="home-page">
    <div class="banner-section">
      <div class="banner-placeholder">个人店铺电商系统</div>
    </div>

    <div class="container">
      <div v-if="!userStore.isLoggedIn" class="guest-welcome">
        <div class="guest-welcome-text">
          <el-icon><View /></el-icon>
          <div>
            <strong>游客浏览模式</strong>
            <span>商品和分类可以自由查看，登录后可加入购物车、收藏并管理订单。</span>
          </div>
        </div>
        <div class="guest-welcome-actions">
          <el-button @click="$router.push('/login')">登录</el-button>
          <el-button type="primary" @click="$router.push('/register')">免费注册</el-button>
        </div>
      </div>

      <section class="category-panel" aria-label="商品分类">
        <div class="category-heading">
          <span>商品分类</span>
          <small>{{ activeCategoryId ? '已筛选分类商品' : '显示全部在售商品' }}</small>
        </div>
        <div class="category-tags">
          <button
            type="button"
            class="category-tag"
            :class="{ active: activeCategoryId === null }"
            :aria-pressed="activeCategoryId === null"
            @click="selectCategory(null)"
          >
            全部商品
          </button>
          <button
            v-for="cat in categories"
            :key="cat.id"
            type="button"
            class="category-tag"
            :class="{ active: activeCategoryId === cat.id }"
            :aria-pressed="activeCategoryId === cat.id"
            @click="selectCategory(cat.id)"
          >
            {{ cat.name }}
          </button>
        </div>
      </section>

      <div class="product-heading">
        <h2 class="section-title">{{ productSectionTitle }}</h2>
        <span class="product-count">共 {{ products.length }} 件商品</span>
      </div>

      <div v-loading="productLoading" class="product-list-wrapper">
        <el-row v-if="products.length > 0" :gutter="20">
          <el-col
            v-for="product in products"
            :key="product.id"
            :xs="24"
            :sm="12"
            :lg="6"
            class="product-column"
          >
            <ProductCard :product="product" />
          </el-col>
        </el-row>
        <el-empty
          v-else-if="!productLoading"
          :description="activeCategoryId ? '该分类暂无在售商品' : '暂无商品'"
        />
      </div>

      <div class="quick-links" v-if="categories.length || products.length">
        <h3 class="quick-title">快捷入口</h3>
        <el-row :gutter="16" justify="center">
          <el-col :xs="12" :sm="6">
            <el-button type="primary" block @click="$router.push('/cart')" class="quick-btn">
              <ShoppingCart /> 购物车
            </el-button>
          </el-col>
          <el-col :xs="12" :sm="6">
            <el-button type="success" block @click="$router.push('/order')" class="quick-btn">
              <ShoppingBag /> 我的订单
            </el-button>
          </el-col>
          <el-col :xs="12" :sm="6">
            <el-button type="warning" block @click="$router.push('/profile')" class="quick-btn">
              <User /> 个人中心
            </el-button>
          </el-col>
          <el-col :xs="12" :sm="6">
            <el-button type="info" block @click="$router.push('/collect')" class="quick-btn">
              <StarFilled /> 我的收藏
            </el-button>
          </el-col>
        </el-row>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProductCard from '@/components/ProductCard.vue'
import { getCategoryList, getProductList } from '@/api/productApi'
import { useUserStore } from '@/stores/userStore'
import { ShoppingCart, ShoppingBag, User, StarFilled, View } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const categories = ref<any[]>([])
const products = ref<any[]>([])
const activeCategoryId = ref<number | null>(null)
const productLoading = ref(false)
let productRequestId = 0

const parseCategoryId = (value: unknown): number | null => {
  const raw = Array.isArray(value) ? value[0] : value
  const id = Number(raw)
  return Number.isInteger(id) && id > 0 ? id : null
}

const activeCategoryName = computed(() => {
  return categories.value.find((category) => category.id === activeCategoryId.value)?.name || ''
})

const productSectionTitle = computed(() => {
  return activeCategoryId.value ? `${activeCategoryName.value || '分类'}商品` : '全部商品'
})

const loadProducts = async () => {
  const requestId = ++productRequestId
  productLoading.value = true
  try {
    const res = await getProductList({
      page: 1,
      size: 100,
      categoryId: activeCategoryId.value || undefined,
      status: 1
    })
    if (requestId === productRequestId) {
      products.value = res.data?.list || []
    }
  } finally {
    if (requestId === productRequestId) {
      productLoading.value = false
    }
  }
}

const selectCategory = (categoryId: number | null) => {
  if (activeCategoryId.value === categoryId) {
    loadProducts()
    return
  }
  router.push({
    path: '/home',
    query: categoryId ? { category: String(categoryId) } : {}
  })
}

watch(
  () => route.query.category,
  (category) => {
    activeCategoryId.value = parseCategoryId(category)
    loadProducts()
  },
  { immediate: true }
)

onMounted(async () => {
  const res = await getCategoryList()
  categories.value = res.data || []
})
</script>

<style scoped>
.banner-section {
  height: 400px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 36px;
  font-weight: bold;
  margin-bottom: 30px;
  border-radius: 8px;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.guest-welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
  padding: 16px 18px;
  border: 1px solid #dbe9f7;
  border-radius: 8px;
  background: #f7fbff;
}

.guest-welcome-text {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #49657f;
}

.guest-welcome-text .el-icon {
  font-size: 24px;
  color: #409eff;
  flex-shrink: 0;
}

.guest-welcome-text div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.guest-welcome-text strong {
  color: #24384c;
  font-size: 15px;
}

.guest-welcome-text span {
  font-size: 13px;
}

.guest-welcome-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.category-panel {
  margin-bottom: 26px;
  padding: 18px 20px;
  background: #fff;
  border: 1px solid #e8edf2;
  border-radius: 10px;
}

.category-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.category-heading span {
  color: #2d3a48;
  font-size: 17px;
  font-weight: 700;
}

.category-heading small {
  color: #909399;
  font-size: 12px;
}

.category-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.category-tag {
  min-height: 38px;
  padding: 0 16px;
  border: 1px solid #e4e8ed;
  border-radius: 8px;
  background: #f8fafc;
  color: #536170;
  font: inherit;
  font-size: 14px;
  cursor: pointer;
  transition: border-color 0.2s, background-color 0.2s, color 0.2s;
}

.category-tag:hover {
  border-color: #ff9a74;
  color: #e5532d;
  background: #fff8f5;
}

.category-tag.active {
  border-color: #ff6b35;
  background: #ff6b35;
  color: #fff;
  font-weight: 600;
}

.product-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-title {
  font-size: 21px;
  font-weight: 700;
  color: #2d3a48;
}

.product-count {
  color: #909399;
  font-size: 13px;
}

.product-list-wrapper {
  min-height: 180px;
}

.product-column {
  margin-bottom: 20px;
}

.quick-links {
  margin-top: 30px;
  padding: 24px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.quick-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  text-align: center;
}

.quick-btn {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
}

@media (max-width: 640px) {
  .banner-section {
    height: 220px;
    font-size: 28px;
  }

  .guest-welcome {
    align-items: stretch;
    flex-direction: column;
  }

  .guest-welcome-actions {
    justify-content: flex-end;
  }

  .category-panel {
    padding: 16px 14px;
  }

  .category-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .category-tag {
    padding: 0 13px;
    font-size: 13px;
  }

  .quick-links {
    padding: 18px 14px;
  }
}
</style>
