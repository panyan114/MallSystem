<template>
  <div class="auth-page">
    <section class="auth-brand">
      <div class="brand-mark">M</div>
      <p class="brand-eyebrow">PERSONAL STORE</p>
      <h1>个人店铺电商系统</h1>
      <p class="brand-copy">登录后即可管理购物车、收藏和订单。</p>
      <el-button class="guest-button" plain @click="$router.push('/home')">
        <el-icon><HomeFilled /></el-icon>
        先浏览商品
      </el-button>
    </section>

    <section class="auth-panel">
      <div class="form-wrap">
        <div class="form-heading">
          <p class="form-kicker">欢迎回来</p>
          <h2>账号登录</h2>
          <p>使用用户名或手机号继续</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="handleLogin"
        >
          <el-form-item label="账号" prop="username">
            <el-input
              v-model="form.username"
              size="large"
              placeholder="用户名或手机号"
              autocomplete="username"
              clearable
            >
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
              show-password
            >
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            class="submit-button"
            native-type="submit"
            :loading="loading"
          >
            登录
            <el-icon class="button-icon"><ArrowRight /></el-icon>
          </el-button>
        </el-form>

        <p class="switch-text">
          还没有账号？<router-link to="/register">立即注册</router-link>
        </p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/userStore'
import { login } from '@/api/authApi'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowRight, HomeFilled, Lock, User } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = ref({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名或手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const res = await login({ username: form.value.username, password: form.value.password })
    userStore.setSession(res.data)
    ElMessage.success('登录成功')
    const requestedRedirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const redirect = requestedRedirect.startsWith('/') && !requestedRedirect.startsWith('//')
      ? requestedRedirect
      : '/home'
    router.replace(redirect)
  } catch (e: any) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(360px, 0.9fr) minmax(460px, 1.1fr);
  background: #f3f1ed;
  overflow: auto;
}

.auth-brand {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-start;
  padding: 72px 9vw;
  background: #17202c;
  color: #f8fafc;
}

.brand-mark {
  width: 54px;
  height: 54px;
  display: grid;
  place-items: center;
  margin-bottom: 28px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 14px;
  color: #ff7043;
  font-size: 26px;
  font-weight: 800;
}

.brand-eyebrow {
  color: #ff8a65;
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 12px;
}

.auth-brand h1 {
  max-width: 430px;
  font-size: 42px;
  line-height: 1.18;
  margin-bottom: 18px;
}

.brand-copy {
  max-width: 390px;
  color: #aeb8c5;
  line-height: 1.8;
  margin-bottom: 34px;
}

.guest-button {
  border-color: #536170;
  color: #e5eaf0;
  background: transparent;
}

.guest-button:hover {
  border-color: #ff7043;
  color: #ff8a65;
  background: rgba(255, 112, 67, 0.08);
}

.auth-panel {
  display: grid;
  place-items: center;
  padding: 48px 24px;
}

.form-wrap {
  width: min(100%, 420px);
}

.form-heading {
  margin-bottom: 30px;
}

.form-kicker {
  color: #e5532d;
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 8px;
}

.form-heading h2 {
  font-size: 30px;
  color: #17202c;
  margin-bottom: 8px;
}

.form-heading > p:last-child,
.switch-text {
  color: #7a8491;
  font-size: 14px;
}

.submit-button {
  width: 100%;
  height: 48px;
  margin-top: 6px;
  font-size: 16px;
}

.button-icon {
  margin-left: 8px;
}

.switch-text {
  margin-top: 24px;
  text-align: center;
}

.switch-text a {
  color: #e5532d;
  font-weight: 600;
}

@media (max-width: 860px) {
  .auth-page {
    grid-template-columns: 1fr;
  }

  .auth-brand {
    min-height: 260px;
    padding: 42px 28px;
  }

  .auth-panel {
    padding: 32px 20px 42px;
  }

  .auth-brand h1 {
    font-size: 32px;
  }

  .brand-copy {
    margin-bottom: 22px;
  }
}
</style>
