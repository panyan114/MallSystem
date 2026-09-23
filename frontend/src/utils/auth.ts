import { ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/userStore'

export const ensureLoggedIn = async (message = '登录后即可使用该功能') => {
  const userStore = useUserStore()
  if (userStore.isLoggedIn) return true

  const router = useRouter()
  try {
    await ElMessageBox.confirm(message, '请先登录', {
      confirmButtonText: '去登录',
      cancelButtonText: '继续浏览',
      type: 'warning'
    })
    router.push({
      path: '/login',
      query: { redirect: router.currentRoute.value.fullPath }
    })
  } catch {
    // 用户选择继续浏览，不打断当前操作。
  }
  return false
}
