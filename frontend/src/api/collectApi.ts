import service from './axios'

export const addCollect = (data: { productId: number }) => service.post('/collect', data)

export const removeCollect = (id: number) => service.delete(`/collect/${id}`)

export const getCollectList = () => service.get('/collect/list')

export const getCollectDetail = (id: number) => service.get(`/collect/${id}`)

export const updateCollect = (id: number, data: { productId: number }) => service.put(`/collect/${id}`, data)
