<template>
  <div class="auth-page">
    <section class="auth-brand">
      <div class="brand-mark">M</div>
      <p class="brand-eyebrow">CREATE ACCOUNT</p>
      <h1>创建你的商城账号</h1>
      <p class="brand-copy">注册后即可加入购物车、收藏商品并管理订单。</p>
      <el-button class="guest-button" plain @click="$router.push('/home')">
        <el-icon><HomeFilled /></el-icon>
        先浏览商品
      </el-button>
    </section>

    <section class="auth-panel">
      <div class="form-wrap">
        <div class="form-heading">
          <p class="form-kicker">新用户注册</p>
          <h2>注册账号</h2>
          <p>填写基础信息后会自动登录</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="handleRegister"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              size="large"
              placeholder="2-20个字符"
              autocomplete="username"
              clearable
            >
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input
              v-model="form.phone"
              size="large"
              placeholder="请输入11位手机号"
              autocomplete="tel"
              clearable
            >
              <template #prefix><el-icon><Iphone /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              placeholder="至少6个字符"
              autocomplete="new-password"
              show-password
            >
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              size="large"
              type="password"
              placeholder="请再次输入密码"
              autocomplete="new-password"
              show-password
            >
              <template #prefix><el-icon><CircleCheck /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            class="submit-button"
            native-type="submit"
            :loading="loading"
          >
            注册并登录
            <el-icon class="button-icon"><ArrowRight /></el-icon>
          </el-button>
        </el-form>

        <p class="switch-text">
          已有账号？<router-link to="/login">立即登录</router-link>
        </p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/userStore'
import { register } from '@/api/authApi'
import { ElMessage, type FormInstance, type FormItemRule, type FormRules } from 'element-plus'
import { ArrowRight, CircleCheck, HomeFilled, Iphone, Lock, User } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = ref({ username: '', phone: '', password: '', confirmPassword: '' })

const validateConfirmPassword: FormItemRule['validator'] = (_rule, value, callback) => {
  if (!value) {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.value.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度需为2到20个字符', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 30, message: '密码长度需为6到30个字符', trigger: 'blur' }
  ],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }]
}

const handleRegister = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const res = await register({ username: form.value.username, password: form.value.password, phone: form.value.phone })
    userStore.setSession(res.data)
    ElMessage.success('注册成功')
    router.replace('/home')
  } catch (e: any) {
    ElMessage.error(e.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(360px, 0.9fr) minmax(480px, 1.1fr);
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
  font-size: 40px;
  line-height: 1.2;
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
  padding: 40px 24px;
}

.form-wrap {
  width: min(100%, 430px);
}

.form-heading {
  margin-bottom: 22px;
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
  margin-top: 20px;
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
    min-height: 250px;
    padding: 38px 28px;
  }

  .auth-panel {
    padding: 30px 20px 42px;
  }

  .auth-brand h1 {
    font-size: 30px;
  }

  .brand-copy {
    margin-bottom: 20px;
  }
}
</style>
