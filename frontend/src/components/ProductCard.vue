<template>
  <el-card class="product-card" shadow="hover" @click="$router.push(`/product/${product.id}`)">
    <img :src="product.images ? JSON.parse(product.images)[0] : ''" class="product-image" alt="">
    <div class="product-info">
      <h3 class="product-name">{{ product.name }}</h3>
      <p class="product-subtitle">{{ product.subtitle }}</p>
      <div class="product-price">
        <span class="current-price">¥{{ product.salePrice || product.price }}</span>
        <span v-if="product.salePrice && product.salePrice !== product.price" class="original-price">¥{{ product.price }}</span>
      </div>
      <div class="product-sales">月销 {{ product.sales }}件</div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
defineProps<{
  product: {
    id: number
    name: string
    subtitle: string
    images: string
    price: number
    salePrice?: number
    sales: number
  }
}>()
</script>

<style scoped>
.product-card {
  cursor: pointer;
}
.product-image {
  width: 100%;
  height: 200px;
  object-fit: cover;
}
.product-info {
  padding: 12px 0;
}
.product-name {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-subtitle {
  font-size: 13px;
  color: #999;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product-price {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.current-price {
  color: #e4393c;
  font-size: 18px;
  font-weight: bold;
}
.original-price {
  color: #999;
  font-size: 13px;
  text-decoration: line-through;
}
.product-sales {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
</style>
