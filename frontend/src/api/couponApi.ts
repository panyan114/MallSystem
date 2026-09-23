import service from './axios'
import type { CouponDTO } from '@/types'

/** 后台：券列表（含停用和过期的，需店主权限） */
export const getCouponList = () => service.get('/coupon/list')

/** 后台：创建优惠券（需店主权限） */
export const createCoupon = (data: CouponDTO) => service.post('/coupon', data)

/** 领券中心：当前可领取的券（含已领取的，带 received 标记） */
export const getAvailableCoupons = () => service.get('/coupon/available')

/** 领取优惠券 */
export const receiveCoupon = (id: number) => service.post(`/coupon/${id}/receive`)

/** 我的优惠券，返回 { list, total } */
export const getMyCoupons = () => service.get('/coupon/my')
