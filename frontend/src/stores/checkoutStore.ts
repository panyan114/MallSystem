import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface CheckoutItem {
  id: number | string
  productId: number
  skuId?: number
  productName: string
  skuDesc: string
  price: number
  quantity: number
  stock: number
  image: string
}

export const useCheckoutStore = defineStore('checkout', () => {
  const directItem = ref<CheckoutItem | null>(null)

  const setDirectItem = (item: CheckoutItem) => {
    directItem.value = item
  }

  const clearDirectItem = () => {
    directItem.value = null
  }

  return { directItem, setDirectItem, clearDirectItem }
})
