<template>
  <div class="price-display">
    <span class="sale-price">¥{{ salePrice }}</span>
    <span v-if="originalPrice && originalPrice !== salePrice" class="original-price">¥{{ originalPrice }}</span>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  originalPrice: number
  salePrice: number
}>()
</script>

<style scoped>
.price-display {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.sale-price {
  color: #e4393c;
  font-size: 20px;
  font-weight: bold;
}
.original-price {
  color: #999;
  font-size: 14px;
  text-decoration: line-through;
}
</style>
