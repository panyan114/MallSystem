import axios from 'axios'

const clearSession = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
}

const redirectToLogin = () => {
  clearSession()
  if (window.location.pathname.startsWith('/login') || window.location.pathname.startsWith('/register')) {
    return
  }
  const redirect = encodeURIComponent(window.location.pathname + window.location.search)
  window.location.href = `/login?redirect=${redirect}`
}

const service = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json'
  }
})

service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

service.interceptors.response.use(
  (response) => {
    const { code, message } = response.data
    if (code === 200) {
      return response.data
    }
    if (code === 401) {
      redirectToLogin()
    }
    return Promise.reject(new Error(message || '请求失败'))
  },
  (error) => {
    if (error.response?.status === 401 || error.response?.data?.code === 401) {
      redirectToLogin()
    }
    return Promise.reject(new Error(error.response?.data?.message || error.message || '请求失败'))
  }
)

export default service
