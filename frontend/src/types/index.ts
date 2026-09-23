export interface Product {
  id: number
  categoryId: number
  name: string
  subtitle: string
  description: string
  images: string
  price: number
  salePrice?: number
  stock: number
  status: number
  sort: number
  sales: number
  createTime: string
  updateTime: string
}

export interface Sku {
  id: number
  productId: number
  specKey: string
  specDesc: string
  price: number
  stock: number
  image?: string
}

export interface ProductDTO {
  categoryId: number
  name: string
  subtitle?: string
  description?: string
  images?: string
  price: number
  salePrice?: number | null
  stock: number
  status: number
  sort: number
  skus?: Sku[]
}

export interface Order {
  id: number
  orderNo: string
  userId: number
  totalAmount: number
  realAmount: number
  status: number
  address: string
  receiver: string
  phone: string
  remark: string
  createTime: string
  payTime?: string
  shipTime?: string
  confirmTime?: string
}

export interface OrderItem {
  id: number
  orderId: number
  productId: number
  skuId?: number
  productName: string
  productImage?: string
  skuDesc?: string
  price: number
  quantity: number
  totalPrice: number
}

export interface OrderDTO {
  address: string
  receiver: string
  phone: string
  remark?: string
  couponId?: number
  clearCart?: boolean
  items: Array<{
    productId: number
    skuId?: number
    quantity: number
  }>
}

export interface Category {
  id: number
  name: string
  parentId: number
  sort: number
  icon?: string
  status: number
  createTime: string
}
