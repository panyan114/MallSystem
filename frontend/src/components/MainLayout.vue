<template>
  <el-container class="layout-container">
    <el-header class="main-header">
      <div class="header-inner">
        <div class="logo" @click="router.push('/home')">
          <span class="logo-mark">M</span>
          <span class="logo-text">个人店铺电商系统</span>
        </div>

        <nav class="main-nav" aria-label="主导航">
          <router-link
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            class="nav-link"
            :class="{ active: isActive(item.path) }"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
            <el-badge
              v-if="item.path === '/cart' && cartStore.totalCount() > 0"
              :value="cartStore.totalCount()"
              :max="99"
              class="cart-badge"
            />
          </router-link>
        </nav>

        <div class="auth-section">
          <template v-if="userStore.isLoggedIn">
            <el-dropdown @command="handleCommand">
              <span class="user-dropdown">
                <el-avatar :size="32" :src="userStore.userInfo?.avatar">
                  {{ userStore.userInfo?.username?.charAt(0) || 'U' }}
                </el-avatar>
                <span class="username">{{ userStore.userInfo?.username }}</span>
                <el-icon class="dropdown-arrow"><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                  <el-dropdown-item command="orders">我的订单</el-dropdown-item>
                  <el-dropdown-item command="collect">我的收藏</el-dropdown-item>
                  <el-dropdown-item v-if="userStore.isAdmin" command="admin">管理后台</el-dropdown-item>
                  <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <span class="guest-label">游客浏览</span>
            <el-button text @click="router.push('/login')">登录</el-button>
            <el-button type="primary" @click="router.push('/register')">注册</el-button>
          </template>
        </div>
      </div>
    </el-header>

    <el-main class="main-content">
      <div class="page-shell">
        <router-view />
      </div>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import {
  ArrowDown,
  Collection,
  HomeFilled,
  List,
  ShoppingCart,
  User
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/userStore'
import { useCartStore } from '@/stores/cartStore'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()

const navItems = [
  { path: '/home', label: '首页', icon: HomeFilled },
  { path: '/cart', label: '购物车', icon: ShoppingCart },
  { path: '/order', label: '我的订单', icon: List },
  { path: '/collect', label: '我的收藏', icon: Collection },
  { path: '/profile', label: '个人中心', icon: User }
]

const isActive = (path: string) => {
  if (path === '/home') return route.path === '/home'
  return route.path.startsWith(path)
}

onMounted(() => {
  if (!userStore.isLoggedIn) return
  userStore.fetchProfile().catch(() => {})
  if (!cartStore.loaded) {
    cartStore.loadCart().catch(() => {})
  }
})

watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    cartStore.reset()
    if (loggedIn) {
      cartStore.loadCart().catch(() => {})
    }
  }
)

const handleCommand = (command: string) => {
  if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
      .then(async () => {
        try {
          await userStore.logout()
          ElMessage.success('已退出登录')
        } catch (e: any) {
          // 服务端吊销失败（例如 Redis 不可用）时，token 在自然过期前仍可能被使用。
          // 本地会话已在 store 中清除，这里如实告知而不是假装成功。
          ElMessage.warning(e?.message || '服务端登出失败，本地登录状态已清除')
        }
        cartStore.reset()
        router.push('/home')
      })
      .catch(() => {})
    return
  }
  if (command === 'orders') {
    router.push('/order')
    return
  }
  router.push(`/${command}`)
}
</script>

<style scoped>
.layout-container {
  min-height: 100vh;
  background: #f4f6f8;
}

.main-header {
  height: auto;
  padding: 0;
  background: #202a36;
  box-shadow: 0 2px 12px rgba(18, 30, 46, 0.16);
  position: sticky;
  top: 0;
  z-index: 1000;
}

.header-inner {
  min-height: 64px;
  max-width: 1320px;
  margin: 0 auto;
  padding: 0 24px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 28px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #fff;
  cursor: pointer;
  white-space: nowrap;
}

.logo-mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #ff6b35;
  color: #fff;
  font-size: 18px;
  font-weight: 800;
}

.logo-text {
  font-size: 17px;
  font-weight: 700;
}

.main-nav {
  display: flex;
  align-items: stretch;
  justify-content: center;
  gap: 4px;
  min-width: 0;
}

.nav-link {
  height: 40px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-radius: 8px;
  color: #bdc7d3;
  font-size: 14px;
  font-weight: 500;
  transition: color 0.2s, background-color 0.2s;
  white-space: nowrap;
}

.nav-link:hover,
.nav-link.active {
  color: #fff;
  background: rgba(255, 255, 255, 0.09);
}

.nav-link.active {
  color: #ff8a5c;
}

.cart-badge {
  margin-left: 2px;
}

.cart-badge :deep(.el-badge__content) {
  border: 0;
}

.auth-section {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 130px;
}

.guest-label {
  color: #8e9bab;
  font-size: 13px;
  white-space: nowrap;
}

.auth-section :deep(.el-button.is-text) {
  color: #e8edf3;
}

.auth-section :deep(.el-button.is-text:hover) {
  color: #ff8a5c;
  background: rgba(255, 255, 255, 0.07);
}

.user-dropdown {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #fff;
  padding: 6px 8px;
  border-radius: 8px;
  transition: background-color 0.2s;
}

.user-dropdown:hover {
  background: rgba(255, 255, 255, 0.08);
}

.username {
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.dropdown-arrow {
  color: #9da9b7;
  font-size: 12px;
}

.main-content {
  padding: 0;
}

.page-shell {
  width: 100%;
  max-width: 1240px;
  margin: 0 auto;
  padding: 22px 20px 40px;
}

@media (max-width: 980px) {
  .header-inner {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 12px;
    padding: 10px 16px 8px;
  }

  .main-nav {
    grid-column: 1 / -1;
    grid-row: 2;
    justify-content: flex-start;
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .auth-section {
    min-width: auto;
  }

  .guest-label {
    display: none;
  }
}

@media (max-width: 640px) {
  .logo-text {
    font-size: 15px;
  }

  .nav-link {
    padding: 0 10px;
  }

  .nav-link span:not(.el-badge) {
    font-size: 13px;
  }

  .username {
    display: none;
  }

  .page-shell {
    padding: 16px 12px 32px;
  }
}
</style>
