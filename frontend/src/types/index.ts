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
  /** 使用的优惠券模板 id（t_coupon.id），仅用于统计 */
  couponId?: number
  /** 使用的用户券 id（t_user_coupon.id），取消订单时据此退回 */
  userCouponId?: number
  /** 优惠金额，未用券为 0.00。恒有 totalAmount - discountAmount == realAmount */
  discountAmount?: number
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
  /** 要使用的用户券 id（t_user_coupon.id）——从「我的优惠券」里选中的那条，不是券模板 id */
  userCouponId?: number
  clearCart?: boolean
  items: Array<{
    productId: number
    skuId?: number
    quantity: number
  }>
}

/** 券类型：0=满减（discountValue 是减免金额），1=折扣（discountValue 是实付比例，0.9 表示九折） */
export const COUPON_TYPE_FULL_REDUCTION = 0
export const COUPON_TYPE_DISCOUNT = 1

/** 领券中心里的一张券 */
export interface Coupon {
  id: number
  name: string
  type: number
  minAmount: number
  discountValue: number
  totalCount: number
  receivedCount: number
  status: number
  startTime?: string
  endTime?: string
  /** 当前用户是否已领取 */
  received: boolean
  remainCount: number
}

/** 我持有的一张券。id 是 t_user_coupon.id —— 下单时提交的就是它 */
export interface UserCoupon {
  id: number
  couponId: number
  name: string
  type: number
  minAmount: number
  discountValue: number
  /** 0=未使用，1=已使用，2=已过期（2 由后端读时派生） */
  status: number
  receiveTime?: string
  useTime?: string
  startTime?: string
  endTime?: string
}

export interface CouponDTO {
  name: string
  type: number
  minAmount: number
  discountValue: number
  totalCount: number
  /** 必须是 ISO 格式（2026-01-01T00:00:00），空格分隔会让后端报 400 */
  startTime?: string
  endTime?: string
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
