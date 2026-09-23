import service from './axios'
import type { OrderDTO } from '@/types'

export const createOrder = (data: OrderDTO) => service.post('/order', data)

export const getOrderList = (params?: any) => service.get('/order/list', { params })

export const getOrderDetail = (id: number, params?: any) => service.get(`/order/${id}`, { params })

export const cancelOrder = (id: number, params?: any) => service.put(`/order/${id}/cancel`, null, { params })

export const payOrder = (id: number, params?: any) => service.post(`/order/${id}/pay`, null, { params })

export const confirmOrder = (id: number, params?: any) => service.put(`/order/${id}/confirm`, null, { params })

export const shipOrder = (id: number, data: { logisticsCompany: string; logisticsNo: string }) =>
  service.post(`/order/${id}/ship`, data)

export const applyRefund = (id: number) => service.post(`/order/${id}/refund`)
