<template>
  <div class="profile-page">
    <el-card class="profile-card">
      <div class="profile-header">
        <el-avatar :size="80" :src="userStore.userInfo?.avatar">
          {{ userStore.userInfo?.username?.charAt(0) || 'U' }}
        </el-avatar>
        <div class="profile-info">
          <h2>{{ userStore.userInfo?.username }}</h2>
          <p>{{ userStore.userInfo?.phone }}</p>
        </div>
      </div>
      <el-tabs>
        <el-tab-pane label="个人信息">
          <el-form :model="editForm" label-width="100px">
            <el-form-item label="用户名">{{ userStore.userInfo?.username }}</el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="editForm.phone" />
            </el-form-item>
            <el-form-item label="头像">
              <el-input v-model="editForm.avatar" placeholder="头像URL" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveProfile">保存</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="修改密码">
          <el-form :model="passwordForm" label-width="120px">
            <el-form-item label="原密码">
              <el-input type="password" v-model="passwordForm.oldPassword" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input type="password" v-model="passwordForm.newPassword" />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input type="password" v-model="passwordForm.confirmPassword" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="changePassword">修改</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useUserStore } from '@/stores/userStore'
import { updateInfo, updatePassword } from '@/api/authApi'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()
const editForm = ref({ phone: '', avatar: '' })
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })

const saveProfile = async () => {
  await updateInfo(editForm.value)
  ElMessage.success('修改成功')
}

const changePassword = async () => {
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.error('两次密码不一致')
    return
  }
  await updatePassword(passwordForm.value)
  ElMessage.success('密码修改成功')
}
</script>

<style scoped>
.profile-page {
  display: flex;
  justify-content: center;
  padding: 20px;
}
.profile-card {
  width: 800px;
}
.profile-header {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 20px;
}
.profile-info h2 {
  margin-bottom: 4px;
}
.profile-info p {
  color: #999;
}
</style>
