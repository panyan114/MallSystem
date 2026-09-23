import service from './axios'

export const getCouponList = () => service.get('/coupon/list')

export const createCoupon = (data: any) => service.post('/coupon', data)

export const receiveCoupon = (id: number) => service.post(`/coupon/${id}/receive`)

export const getMyCoupons = () => service.get('/coupon/my')
