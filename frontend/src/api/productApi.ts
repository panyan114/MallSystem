import service from './axios'
import type { ProductDTO } from '@/types'

export const getProductList = (params?: any) => service.get('/product/list', { params })

export const getProductDetail = (id: number) => service.get(`/product/${id}`)

export const createProduct = (data: ProductDTO) => service.post('/product', data)

export const updateProduct = (id: number, data: ProductDTO) => service.put(`/product/${id}`, data)

export const updateProductStatus = (id: number, status?: number) =>
  service.put(`/product/${id}/status`, null, { params: status == null ? undefined : { status } })

export const deleteProduct = (id: number) => service.delete(`/product/${id}`)

export const getCategoryList = () => service.get('/category/list')

export const createCategory = (data: any) => service.post('/category', data)

export const updateCategory = (data: any) => service.put('/category', data)

export const deleteCategory = (id: number) => service.delete(`/category/${id}`)
