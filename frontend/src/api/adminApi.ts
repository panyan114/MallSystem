import service from './axios'

export const getDashboard = () => service.get('/stat/dashboard')

export const getProductRanking = () => service.get('/stat/product-ranking')

export const getOrderStats = () => service.get('/stat/order-stats')
