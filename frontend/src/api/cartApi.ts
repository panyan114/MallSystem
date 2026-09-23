import service from './axios'

export const addToCart = (data: any) => service.post('/cart', data)

export const getCartList = () => service.get('/cart')

export const updateCartQuantity = (id: number, quantity: number) =>
  service.put(`/cart/${id}`, { quantity })

export const removeFromCart = (id: number) => service.delete(`/cart/${id}`)

export const clearCart = () => service.delete('/cart')
