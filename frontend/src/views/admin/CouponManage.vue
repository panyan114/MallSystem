<template>
  <div class="coupon-manage-page">
    <div class="toolbar">
      <el-button type="primary" @click="openAddDialog">发放优惠券</el-button>
      <el-button @click="loadCoupons">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="couponList" stripe border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">
          {{ row.type === 0 ? '满减' : '折扣' }}
        </template>
      </el-table-column>
      <el-table-column label="优惠值" width="120">
        <template #default="{ row }">
          {{ valueText(row) }}
        </template>
      </el-table-column>
      <el-table-column label="最低金额" width="110">
        <template #default="{ row }">
          ¥{{ formatPrice(row.minAmount) }}
        </template>
      </el-table-column>
      <el-table-column label="有效期" min-width="200">
        <template #default="{ row }">
          <span class="period">{{ formatTime(row.startTime) }} ~ {{ formatTime(row.endTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="totalCount" label="总数量" width="90" />
      <el-table-column prop="receivedCount" label="已领取" width="90" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <!-- 后端目前没有删除优惠券的接口，这里如实禁用而不是弹一个假的「删除成功」 -->
          <el-tooltip content="暂不支持删除优惠券" placement="top">
            <el-button size="small" type="danger" disabled>删除</el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="addDialogVisible" title="发放优惠券" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：新人立减券" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="满减" :value="0" />
            <el-option label="折扣" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item :label="form.type === 1 ? '折扣比例' : '减免金额'" prop="discountValue">
          <el-input-number
            v-model="form.discountValue"
            :min="form.type === 1 ? 0.01 : 0.01"
            :max="form.type === 1 ? 0.99 : undefined"
            :step="form.type === 1 ? 0.05 : 1"
            :precision="2"
            style="width: 100%"
          />
          <span class="field-hint">
            {{ form.type === 1 ? '0.9 表示九折，须在 0 与 1 之间' : '例如满 50 减 10，这里填 10' }}
          </span>
        </el-form-item>
        <el-form-item label="最低金额" prop="minAmount">
          <el-input-number v-model="form.minAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="总数量" prop="totalCount">
          <el-input-number v-model="form.totalCount" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <!-- value-format 必须是 ISO 的 T 分隔格式。用默认的 "YYYY-MM-DD HH:mm:ss"
               会让后端 Jackson 反序列化 LocalDateTime 失败，报 400「请求体格式错误」 -->
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择结束时间"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCoupon">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createCoupon, getCouponList } from '@/api/couponApi'
import type { CouponDTO } from '@/types'

const couponList = ref<any[]>([])
const addDialogVisible = ref(false)
const saving = ref(false)
const loading = ref(false)
const formRef = ref<FormInstance>()

const emptyForm = (): CouponDTO => ({
  name: '',
  type: 0,
  minAmount: 50,
  discountValue: 10,
  totalCount: 100,
  startTime: undefined,
  endTime: undefined
})

const form = ref<CouponDTO>(emptyForm())

const rules: FormRules = {
  name: [{ required: true, message: '请填写优惠券名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  discountValue: [{ required: true, message: '请填写优惠值', trigger: 'blur' }],
  minAmount: [{ required: true, message: '请填写最低金额', trigger: 'blur' }],
  totalCount: [{ required: true, message: '请填写总数量', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}

const formatPrice = (value: any) => Number(value || 0).toFixed(2)

const valueText = (row: any) =>
  row.type === 1
    ? `${(Number(row.discountValue) * 10).toFixed(1).replace(/\.0$/, '')}折`
    : `¥${formatPrice(row.discountValue)}`

const formatTime = (value: any) => {
  if (!value) return '-'
  if (Array.isArray(value)) {
    const [year, month, day, hour, minute, second] = value
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${year}-${pad(month)}-${pad(day)} ${pad(hour)}:${pad(minute)}:${pad(second)}`
  }
  return String(value).replace('T', ' ').slice(0, 19)
}

const loadCoupons = async () => {
  loading.value = true
  try {
    const res = await getCouponList()
    couponList.value = res.data?.list || []
  } finally {
    loading.value = false
  }
}

const openAddDialog = () => {
  form.value = emptyForm()
  formRef.value?.clearValidate()
  addDialogVisible.value = true
}

const saveCoupon = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    await createCoupon(form.value)
    ElMessage.success('优惠券发放成功')
    addDialogVisible.value = false
    await loadCoupons()
  } catch (e: any) {
    ElMessage.error(e.message || '发放失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => { loadCoupons() })
</script>

<style scoped>
.toolbar {
  margin-bottom: 20px;
}

.field-hint {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.period {
  font-size: 12px;
  color: #606266;
}
</style>
