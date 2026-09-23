import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useProductStore = defineStore('product', () => {
  const productList = ref<any[]>([])
  const categories = ref<any[]>([])

  const setProductList = (list: any[]) => {
    productList.value = list
  }

  const setCategories = (list: any[]) => {
    categories.value = list
  }

  return { productList, categories, setProductList, setCategories }
})
