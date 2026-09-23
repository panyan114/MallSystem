import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useOrderStore = defineStore('order', () => {
  const orderList = ref<any[]>([])
  const orderDetail = ref<any>(null)

  const setOrderList = (list: any[]) => {
    orderList.value = list
  }

  const setOrderDetail = (detail: any) => {
    orderDetail.value = detail
  }

  return { orderList, orderDetail, setOrderList, setOrderDetail }
})
