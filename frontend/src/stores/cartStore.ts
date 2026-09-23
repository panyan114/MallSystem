import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  addToCart,
  getCartList,
  updateCartQuantity,
  removeFromCart,
  clearCart as clearCartApi
} from '@/api/cartApi'

export const useCartStore = defineStore('cart', () => {
  const cartList = ref<any[]>([])
  const loading = ref(false)
  const loaded = ref(false)
  const selectedIds = ref<number[]>([])
  const selectionInitialized = ref(false)

  const selectedItems = computed(() => {
    const selected = new Set(selectedIds.value)
    return cartList.value.filter((item) => selected.has(item.id))
  })

  const setCartList = (list: any[]) => {
    cartList.value = list
    loaded.value = true
  }

  const loadCart = async () => {
    loading.value = true
    const hadItems = cartList.value.length > 0
    try {
      const res = await getCartList()
      cartList.value = res.data || []
      const availableIds = cartList.value
        .filter((item) => Number(item.stock || 0) > 0)
        .map((item) => item.id)
      if (!selectionInitialized.value || !hadItems) {
        selectedIds.value = availableIds
        selectionInitialized.value = true
      } else {
        selectedIds.value = selectedIds.value.filter((id) => availableIds.includes(id))
      }
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  const addItem = async (item: { productId: number; skuId?: number; quantity: number }) => {
    await addToCart({
      productId: item.productId,
      skuId: item.skuId,
      quantity: item.quantity
    })
    await loadCart()
  }

  const updateQuantity = async (id: number, quantity: number) => {
    await updateCartQuantity(id, quantity)
    await loadCart()
  }

  const removeItem = async (id: number) => {
    await removeFromCart(id)
    await loadCart()
  }

  const clearCart = async () => {
    await clearCartApi()
    cartList.value = []
    selectedIds.value = []
    selectionInitialized.value = false
    loaded.value = true
  }

  const setSelectedIds = (ids: number[]) => {
    selectedIds.value = ids
    selectionInitialized.value = true
  }

  const reset = () => {
    cartList.value = []
    selectedIds.value = []
    loaded.value = false
    selectionInitialized.value = false
  }

  const totalPrice = () => {
    return cartList.value.reduce(
      (sum, item) => sum + Number(item.price || 0) * Number(item.quantity || 0),
      0
    )
  }

  const totalCount = () => {
    return cartList.value.reduce((sum, item) => sum + Number(item.quantity || 0), 0)
  }

  const selectedPrice = computed(() => {
    return selectedItems.value.reduce(
      (sum, item) => sum + Number(item.price || 0) * Number(item.quantity || 0),
      0
    )
  })

  const selectedCount = computed(() => {
    return selectedItems.value.reduce((sum, item) => sum + Number(item.quantity || 0), 0)
  })

  return {
    cartList,
    loading,
    loaded,
    selectedIds,
    selectedItems,
    selectedPrice,
    selectedCount,
    setCartList,
    loadCart,
    addItem,
    updateQuantity,
    removeItem,
    clearCart,
    setSelectedIds,
    reset,
    totalPrice,
    totalCount
  }
})
