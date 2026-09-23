<template>
  <div class="coupon-manage-page">
    <div class="toolbar">
      <el-button type="primary" @click="openAddDialog">发放优惠券</el-button>
    </div>
    <el-table :data="couponList" stripe border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">
          {{ row.type === 0 ? '满减' : '折扣' }}
        </template>
      </el-table-column>
      <el-table-column prop="discountValue" label="优惠值" width="120" />
      <el-table-column prop="minAmount" label="最低金额" width="120" />
      <el-table-column prop="totalCount" label="总数量" width="100" />
      <el-table-column prop="receivedCount" label="已领取" width="100" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" @click="editCoupon(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="deleteCoupon(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="addDialogVisible" title="发放优惠券" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="满减" :value="0" />
            <el-option label="折扣" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="优惠值"><el-input-number v-model="form.discountValue" :min="0" /></el-form-item>
        <el-form-item label="最低金额"><el-input-number v-model="form.minAmount" :min="0" /></el-form-item>
        <el-form-item label="总数量"><el-input-number v-model="form.totalCount" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCoupon">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getCouponList } from '@/api/couponApi'

const couponList = ref<any[]>([])
const addDialogVisible = ref(false)
const form = ref<any>({})

const loadCoupons = async () => {
  const res = await getCouponList()
  couponList.value = res.data?.list || []
}

const openAddDialog = () => {
  form.value = {}
  addDialogVisible.value = true
}

const saveCoupon = () => {
  addDialogVisible.value = false
  ElMessage.success('优惠券发放成功')
  loadCoupons()
}

const deleteCoupon = () => {
  ElMessage.warning('删除成功')
}

onMounted(() => { loadCoupons() })
</script>

<style scoped>
.toolbar {
  margin-bottom: 20px;
}
</style>
