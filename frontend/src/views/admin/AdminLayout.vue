<template>
  <el-container class="admin-layout">
    <el-aside width="220px" class="admin-aside">
      <button type="button" class="brand" @click="router.push('/admin/dashboard')">
        <span class="brand-mark">M</span>
        <span class="brand-copy">
          <strong>个人店铺</strong>
          <small>商家管理后台</small>
        </span>
      </button>

      <el-menu
        :default-active="route.path"
        background-color="#202a36"
        text-color="#aeb8c5"
        active-text-color="#ffffff"
        router
        class="admin-menu"
      >
        <el-menu-item index="/admin/dashboard">
          <el-icon><TrendCharts /></el-icon>
          <span>数据概览</span>
        </el-menu-item>
        <el-menu-item index="/admin/product">
          <el-icon><Goods /></el-icon>
          <span>商品管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/category">
          <el-icon><Folder /></el-icon>
          <span>分类管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/order">
          <el-icon><ShoppingBag /></el-icon>
          <span>订单管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/coupon">
          <el-icon><Ticket /></el-icon>
          <span>优惠券管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/stat">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据统计</span>
        </el-menu-item>
      </el-menu>

      <div class="aside-footer">
        <span class="status-dot" />
        <span>店铺服务运行中</span>
      </div>
    </el-aside>

    <el-container class="admin-main">
      <el-header class="admin-header">
        <div class="header-copy">
          <h1>{{ pageTitle }}</h1>
          <p>{{ pageDescription }}</p>
        </div>
        <div class="header-actions">
          <el-button text :icon="ArrowLeft" @click="router.push('/home')">返回商城</el-button>
          <span class="account-chip">
            <el-avatar :size="30" :src="userStore.userInfo?.avatar">
              {{ userStore.userInfo?.username?.charAt(0) || '店' }}
            </el-avatar>
            <span>{{ userStore.userInfo?.username || '店主' }}</span>
          </span>
          <el-button text type="danger" :icon="SwitchButton" @click="handleLogout">退出</el-button>
        </div>
      </el-header>

      <el-main class="admin-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  DataAnalysis,
  Folder,
  Goods,
  ShoppingBag,
  SwitchButton,
  Ticket,
  TrendCharts
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/userStore'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const pageDescriptions: Record<string, string> = {
  '/admin/dashboard': '查看店铺经营数据与订单状态',
  '/admin/product': '维护商品信息、库存、价格和上下架状态',
  '/admin/category': '管理商品分类和展示顺序',
  '/admin/order': '处理订单发货、取消和详情查看',
  '/admin/coupon': '配置店铺优惠券和活动规则',
  '/admin/stat': '查看经营统计与商品销售表现'
}

const pageTitle = computed(() => String(route.meta.title || '管理后台'))
const pageDescription = computed(() => pageDescriptions[route.path] || '店铺经营管理')

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '退出登录', { type: 'warning' })
  } catch {
    return
  }
  try {
    await userStore.logout()
    ElMessage.success('已退出登录')
  } catch (e: any) {
    // 服务端吊销失败时本地会话仍已清除（见 userStore.logout 的 finally），
    // 但 token 可能还能用到自然过期，如实告知用户。
    ElMessage.warning(e?.message || '服务端登出失败，本地登录状态已清除')
  }
  router.push('/login')
}
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
  background: #f3f5f7;
}

.admin-aside {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #202a36;
  border-right: 1px solid #2d3947;
}

.brand {
  width: 100%;
  height: 76px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  gap: 11px;
  border: 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: transparent;
  color: #fff;
  text-align: left;
  cursor: pointer;
}

.brand-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 9px;
  background: #ff6b35;
  font-size: 19px;
  font-weight: 800;
}

.brand-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brand-copy strong {
  font-size: 16px;
  line-height: 1.25;
}

.brand-copy small {
  color: #8995a4;
  font-size: 11px;
}

.admin-menu {
  flex: 1;
  border-right: 0;
  padding: 12px 10px;
}

.admin-menu :deep(.el-menu-item) {
  height: 46px;
  margin-bottom: 4px;
  border-radius: 8px;
}

.admin-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.07);
}

.admin-menu :deep(.el-menu-item.is-active) {
  background: #ff6b35;
}

.aside-footer {
  margin: 16px;
  padding: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  color: #8f9baa;
  font-size: 12px;
}

.status-dot {
  width: 7px;
  height: 7px;
  flex-shrink: 0;
  border-radius: 50%;
  background: #49c779;
  box-shadow: 0 0 0 3px rgba(73, 199, 121, 0.14);
}

.admin-main {
  min-width: 0;
}

.admin-header {
  height: 76px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  background: #fff;
  border-bottom: 1px solid #e6eaee;
}

.header-copy {
  min-width: 0;
}

.header-copy h1 {
  margin: 0 0 3px;
  color: #263445;
  font-size: 19px;
  line-height: 1.25;
}

.header-copy p {
  margin: 0;
  color: #8a96a3;
  font-size: 12px;
}

.header-actions,
.account-chip {
  display: flex;
  align-items: center;
}

.header-actions {
  flex-shrink: 0;
  gap: 8px;
}

.account-chip {
  gap: 8px;
  padding: 4px 10px 4px 5px;
  border: 1px solid #e6eaee;
  border-radius: 999px;
  color: #4b5968;
  font-size: 13px;
}

.admin-content {
  min-width: 0;
  padding: 22px;
  background: #f3f5f7;
}

@media (max-width: 900px) {
  .admin-layout {
    display: block;
  }

  .admin-aside {
    width: 100% !important;
    min-height: auto;
  }

  .brand {
    height: 64px;
  }

  .admin-menu {
    display: flex;
    padding: 8px;
    overflow-x: auto;
  }

  .admin-menu :deep(.el-menu-item) {
    min-width: 120px;
    margin: 0 3px 0 0;
  }

  .aside-footer {
    display: none;
  }

  .admin-header {
    height: auto;
    min-height: 70px;
    padding: 12px 16px;
  }

  .header-copy p,
  .account-chip span:last-child {
    display: none;
  }

  .admin-content {
    padding: 14px;
  }
}

@media (max-width: 560px) {
  .header-actions :deep(.el-button span) {
    display: none;
  }

  .header-actions :deep(.el-button) {
    padding: 7px;
  }
}
</style>
