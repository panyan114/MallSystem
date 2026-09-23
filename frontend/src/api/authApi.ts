import service from './axios'
import type { AuthSession, UserInfo } from '@/stores/userStore'

export const register = (data: { username: string; password: string; phone: string }) =>
  service.post<AuthSession>('/auth/register', data)

export const login = (data: { username: string; password: string }) =>
  service.post<AuthSession>('/auth/login', data)

export const logout = () => service.post('/auth/logout')

export const getUserInfo = () => service.get<UserInfo>('/auth/info')

export const updateInfo = (data: { phone?: string; avatar?: string }) => service.put<UserInfo>('/auth/info', data)

export const updatePassword = (data: { oldPassword: string; newPassword: string }) =>
  service.put('/auth/password', data)
