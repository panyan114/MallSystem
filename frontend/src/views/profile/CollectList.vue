<template>
  <div class="collect-page">
    <div class="page-header">
      <div>
        <h2>我的收藏</h2>
        <p class="page-subtitle">管理你收藏的商品，支持新增、查看、编辑和删除</p>
      </div>
      <el-button type="primary" @click="openAddDialog">
        <el-icon><Plus /></el-icon>
        添加收藏
      </el-button>
    </div>

    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索收藏商品"
          clearable
          prefix-icon="Search"
          class="search-input"
        />
        <el-button @click="loadCollects">刷新</el-button>
      </div>
    </el-card>

    <el-table
      v-loading="loading"
      :data="filteredList"
      stripe
      style="width: 100%"
      empty-text="暂无收藏商品"
    >
      <el-table-column label="商品" min-width="320">
        <template #default="{ row }">
          <div class="product-cell">
            <el-image :src="row.image" fit="cover" class="product-thumb">
              <template #error>
                <div class="image-placeholder"><el-icon><Picture /></el-icon></div>
              </template>
            </el-image>
            <div class="product-meta">
              <p class="product-name">{{ row.name }}</p>
              <p class="product-time">收藏时间：{{ formatTime(row.createTime) }}</p>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="价格" width="140">
        <template #default="{ row }">
          <span class="price">¥{{ formatPrice(row.price) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDetail(row)">查看</el-button>
          <el-button size="small" type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="cancelCollect(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination" v-if="filteredList.length > 0">
      <span>共 {{ filteredList.length }} 条收藏</span>
    </div>

    <!-- 新增收藏 -->
    <el-dialog v-model="addDialogVisible" title="添加收藏" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="选择商品">
          <el-select
            v-model="form.productId"
            filterable
            placeholder="请选择要收藏的商品"
            style="width: 100%"
          >
            <el-option
              v-for="product in productOptions"
              :key="product.id"
              :label="`${product.name}（¥${formatPrice(product.salePrice || product.price)}）`"
              :value="product.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAdd">保存</el-button>
      </template>
    </el-dialog>

    <!-- 编辑收藏 -->
    <el-dialog v-model="editDialogVisible" title="编辑收藏" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="收藏商品">
          <el-select
            v-model="form.productId"
            filterable
            placeholder="请选择商品"
            style="width: 100%"
          >
            <el-option
              v-for="product in productOptions"
              :key="product.id"
              :label="`${product.name}（¥${formatPrice(product.salePrice || product.price)}）`"
              :value="product.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 查看详情 -->
    <el-dialog v-model="detailDialogVisible" title="收藏详情" width="560px">
      <div v-if="detail" class="detail-content">
        <el-image :src="detail.image" fit="cover" class="detail-image">
          <template #error>
            <div class="image-placeholder large"><el-icon><Picture /></el-icon></div>
          </template>
        </el-image>
        <div class="detail-info">
          <h3>{{ detail.name }}</h3>
          <p class="detail-price">¥{{ formatPrice(detail.price) }}</p>
          <p class="detail-label">收藏时间：{{ formatTime(detail.createTime) }}</p>
          <p class="detail-label">商品 ID：{{ detail.productId }}</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="goProduct(detail.productId)">查看商品</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Picture } from '@element-plus/icons-vue'
import {
  addCollect,
  removeCollect,
  getCollectList,
  getCollectDetail,
  updateCollect
} from '@/api/collectApi'
import { getProductList } from '@/api/productApi'

const router = useRouter()
const collectList = ref<any[]>([])
const productOptions = ref<any[]>([])
const keyword = ref('')
const loading = ref(false)
const saving = ref(false)
const addDialogVisible = ref(false)
const editDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const detail = ref<any>(null)
const editingId = ref<number | null>(null)
const form = ref<{ productId: number | null }>({ productId: null })

const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return collectList.value
  return collectList.value.filter((item) =>
    String(item.name || '').toLowerCase().includes(kw)
  )
})

const formatPrice = (price: number | string | undefined) => {
  const value = Number(price || 0)
  return value.toFixed(2)
}

const formatTime = (value: any) => {
  if (!value) return '-'
  if (Array.isArray(value)) {
    const [year, month, day, hour, minute, second] = value
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${year}-${pad(month)}-${pad(day)} ${pad(hour)}:${pad(minute)}:${pad(second)}`
  }
  return String(value).replace('T', ' ').slice(0, 19)
}

const loadCollects = async () => {
  loading.value = true
  try {
    const res = await getCollectList()
    collectList.value = res.data || []
  } finally {
    loading.value = false
  }
}

const loadProducts = async () => {
  const res = await getProductList({ page: 1, size: 100 })
  productOptions.value = res.data?.list || []
}

const openAddDialog = async () => {
  form.value = { productId: null }
  addDialogVisible.value = true
  if (productOptions.value.length === 0) {
    await loadProducts()
  }
}

const saveAdd = async () => {
  if (!form.value.productId) {
    ElMessage.warning('请选择商品')
    return
  }
  saving.value = true
  try {
    await addCollect({ productId: form.value.productId })
    ElMessage.success('收藏成功')
    addDialogVisible.value = false
    await loadCollects()
  } catch (e: any) {
    ElMessage.error(e.message || '收藏失败')
  } finally {
    saving.value = false
  }
}

const openEdit = async (row: any) => {
  editingId.value = row.id
  form.value = { productId: row.productId }
  editDialogVisible.value = true
  if (productOptions.value.length === 0) {
    await loadProducts()
  }
}

const saveEdit = async () => {
  if (!editingId.value || !form.value.productId) {
    ElMessage.warning('请选择商品')
    return
  }
  saving.value = true
  try {
    await updateCollect(editingId.value, { productId: form.value.productId })
    ElMessage.success('修改成功')
    editDialogVisible.value = false
    await loadCollects()
  } catch (e: any) {
    ElMessage.error(e.message || '修改失败')
  } finally {
    saving.value = false
  }
}

const openDetail = async (row: any) => {
  const res = await getCollectDetail(row.id)
  detail.value = res.data
  detailDialogVisible.value = true
}

const cancelCollect = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定要删除收藏“${row.name}”吗？`, '提示', { type: 'warning' })
    await removeCollect(row.id)
    ElMessage.success('删除成功')
    await loadCollects()
  } catch {}
}

const goProduct = (productId: number) => {
  detailDialogVisible.value = false
  router.push(`/product/${productId}`)
}

onMounted(() => {
  loadCollects()
})
</script>

<style scoped>
.collect-page {
  padding: 4px 0 24px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-header h2 {
  font-size: 24px;
  margin-bottom: 6px;
  color: #303133;
}

.page-subtitle {
  color: #909399;
  font-size: 13px;
}

.toolbar-card {
  margin-bottom: 16px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-input {
  max-width: 320px;
}

.product-cell {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 4px 0;
}

.product-thumb {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  flex-shrink: 0;
  background: #f5f7fa;
}

.image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  color: #c0c4cc;
}

.image-placeholder.large {
  height: 220px;
  font-size: 42px;
}

.product-name {
  color: #303133;
  font-weight: 600;
  margin-bottom: 6px;
}

.product-time,
.detail-label {
  color: #909399;
  font-size: 12px;
}

.price,
.detail-price {
  color: #e4393c;
  font-size: 18px;
  font-weight: bold;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  padding: 14px 4px;
  color: #909399;
  font-size: 13px;
}

.detail-content {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.detail-image {
  width: 220px;
  height: 220px;
  border-radius: 8px;
  flex-shrink: 0;
  background: #f5f7fa;
}

.detail-info h3 {
  font-size: 20px;
  margin-bottom: 12px;
  color: #303133;
}

.detail-info p {
  margin-bottom: 10px;
}

@media (max-width: 768px) {
  .page-header,
  .toolbar,
  .detail-content {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input {
    max-width: none;
  }

  .detail-image {
    width: 100%;
  }
}
</style>
