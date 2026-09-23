import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getUserInfo, logout as logoutApi } from '@/api/authApi'

export interface UserInfo {
  id: number
  username: string
  phone?: string
  avatar?: string
  role: number
}

export interface AuthSession {
  token: string
  user: UserInfo
}

export const useUserStore = defineStore('user', () => {
  const savedUser = localStorage.getItem('userInfo')
  let initialUser: any = null
  if (savedUser) {
    try {
      initialUser = JSON.parse(savedUser)
    } catch {}
  }

  const userInfo = ref<UserInfo | null>(initialUser)
  const token = ref<string>(localStorage.getItem('token') || '')
  if (!token.value) {
    userInfo.value = null
    localStorage.removeItem('userInfo')
  }
  const isLoggedIn = computed(() => !!token.value && !!userInfo.value)
  const isAdmin = computed(() => userInfo.value?.role === 1)

  const setUser = (info: UserInfo, tokenStr: string) => {
    userInfo.value = info
    token.value = tokenStr
    localStorage.setItem('userInfo', JSON.stringify(info))
    localStorage.setItem('token', tokenStr)
  }

  const setSession = (session: AuthSession) => {
    setUser(session.user, session.token)
  }

  const fetchProfile = async () => {
    if (!token.value) return null
    const res = await getUserInfo()
    userInfo.value = res.data
    localStorage.setItem('userInfo', JSON.stringify(res.data))
    return res.data
  }

  const clearUser = () => {
    userInfo.value = null
    token.value = ''
    localStorage.removeItem('userInfo')
    localStorage.removeItem('token')
  }

  const logout = async () => {
    try {
      if (token.value) {
        await logoutApi()
      }
    } finally {
      clearUser()
    }
  }

  return {
    userInfo,
    token,
    isLoggedIn,
    isAdmin,
    setUser,
    setSession,
    fetchProfile,
    clearUser,
    logout
  }
})
