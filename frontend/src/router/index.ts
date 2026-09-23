import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/userStore'

const routes = [
  {
    path: '/',
    redirect: '/home',
    meta: { title: '首页' }
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/home/HomeView.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/product/:id',
    name: 'ProductDetail',
    component: () => import('@/views/product/ProductDetail.vue'),
    meta: { title: '商品详情' }
  },
  {
    path: '/cart',
    name: 'Cart',
    component: () => import('@/views/cart/CartView.vue'),
    meta: { title: '购物车', requiresAuth: true }
  },
  {
    path: '/order',
    name: 'Order',
    component: () => import('@/views/order/OrderList.vue'),
    meta: { title: '我的订单', requiresAuth: true }
  },
  {
    path: '/order/confirm',
    name: 'OrderConfirm',
    component: () => import('@/views/order/OrderConfirm.vue'),
    meta: { title: '确认订单', requiresAuth: true }
  },
  {
    path: '/order/detail/:id',
    name: 'OrderDetail',
    component: () => import('@/views/order/OrderDetail.vue'),
    meta: { title: '订单详情', requiresAuth: true }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', guestOnly: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/login/RegisterView.vue'),
    meta: { title: '注册', guestOnly: true }
  },
  {
    path: '/collect',
    name: 'Collect',
    component: () => import('@/views/profile/CollectList.vue'),
    meta: { title: '我的收藏', requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/profile/ProfileView.vue'),
    meta: { title: '个人中心', requiresAuth: true }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    meta: { title: '管理后台', requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/DashboardView.vue'),
        meta: { title: '数据概览' }
      },
      {
        path: 'product',
        name: 'ProductManage',
        component: () => import('@/views/admin/ProductManage.vue'),
        meta: { title: '商品管理' }
      },
      {
        path: 'category',
        name: 'CategoryManage',
        component: () => import('@/views/admin/CategoryManage.vue'),
        meta: { title: '分类管理' }
      },
      {
        path: 'order',
        name: 'OrderManage',
        component: () => import('@/views/admin/OrderManage.vue'),
        meta: { title: '订单管理' }
      },
      {
        path: 'order/:id',
        name: 'AdminOrderDetail',
        component: () => import('@/views/order/OrderDetail.vue'),
        meta: { title: '订单详情' }
      },
      {
        path: 'coupon',
        name: 'CouponManage',
        component: () => import('@/views/admin/CouponManage.vue'),
        meta: { title: '优惠券管理' }
      },
      {
        path: 'stat',
        name: 'StatView',
        component: () => import('@/views/admin/StatView.vue'),
        meta: { title: '数据统计' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.meta.guestOnly && userStore.isLoggedIn) {
    return '/home'
  }
  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再操作')
    return {
      path: '/login',
      query: { redirect: to.fullPath }
    }
  }
  if (to.meta.requiresAdmin && !userStore.isAdmin) {
    ElMessage.warning('该页面仅店主可访问')
    return '/home'
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta?.title || '个人店铺电商系统'
  document.title = `${title} - 个人店铺电商系统`
})

export default router
