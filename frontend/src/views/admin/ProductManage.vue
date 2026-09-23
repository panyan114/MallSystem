<template>
  <div class="product-manage-page">
    <div class="page-header">
      <div>
        <h2>商品管理</h2>
        <p>管理商品上下架、库存和促销价格。</p>
      </div>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        新增商品
      </el-button>
    </div>

    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索商品名称"
          clearable
          class="search-input"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="statusFilter" placeholder="全部状态" clearable class="status-select">
          <el-option label="上架中" :value="1" />
          <el-option label="已下架" :value="0" />
        </el-select>
        <el-button @click="loadProducts">刷新</el-button>
        <el-button
          type="warning"
          plain
          :disabled="selection.length === 0"
          :loading="batchLoading"
          @click="batchOffline"
        >
          批量下架
        </el-button>
      </div>
    </el-card>

    <el-table
      v-loading="loading"
      :data="filteredProducts"
      stripe
      border
      row-key="id"
      @selection-change="handleSelection"
    >
      <el-table-column type="selection" width="52" />
      <el-table-column prop="id" label="ID" width="72" />
      <el-table-column label="商品" min-width="230">
        <template #default="{ row }">
          <div class="product-cell">
            <el-image :src="firstImage(row.images)" fit="cover" class="product-thumb">
              <template #error>
                <div class="image-placeholder"><el-icon><Picture /></el-icon></div>
              </template>
            </el-image>
            <div class="product-meta">
              <strong>{{ row.name }}</strong>
              <span>{{ row.subtitle || '暂无副标题' }}</span>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="分类" width="120">
        <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
      </el-table-column>
      <el-table-column label="价格设置" min-width="190">
        <template #default="{ row }">
          <div class="price-cell">
            <div>
              <span class="current-price">¥{{ formatPrice(row.salePrice || row.price) }}</span>
              <span v-if="row.salePrice" class="original-price">¥{{ formatPrice(row.price) }}</span>
            </div>
            <el-tag v-if="row.salePrice" type="danger" size="small">
              优惠 {{ discountPercent(row) }}%
            </el-tag>
            <span v-else class="no-discount">未设置优惠</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="82" />
      <el-table-column prop="sales" label="销量" width="82" />
      <el-table-column label="状态" width="92">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '上架中' : '已下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
          <el-button
            size="small"
            :type="row.status === 1 ? 'warning' : 'success'"
            :loading="statusLoadingId === row.id"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
          <el-button size="small" type="danger" @click="deleteProduct(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无符合条件的商品" />
      </template>
    </el-table>

    <el-dialog
      v-model="editDialogVisible"
      :title="isEdit ? '编辑商品与优惠' : '新增商品'"
      width="760px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="商品分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" maxlength="200" show-word-limit placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="form.subtitle" maxlength="500" placeholder="例如：轻薄便携，静音办公" />
        </el-form-item>
        <el-form-item label="商品主图">
          <el-input v-model="form.imageUrl" placeholder="请输入商品图片 URL，例如 /images/p1.svg" />
        </el-form-item>
        <el-form-item label="价格设置" required>
          <div class="price-setting">
            <div class="price-field">
              <span>原价</span>
              <el-input-number
                v-model="form.price"
                :min="0.01"
                :max="9999999"
                :precision="2"
                :step="1"
                controls-position="right"
              />
            </div>
            <div class="price-field">
              <div class="price-label-row">
                <span>优惠价</span>
                <el-switch v-model="promotionEnabled" active-text="开启优惠" />
              </div>
              <el-input-number
                v-model="form.salePrice"
                :disabled="!promotionEnabled"
                :min="0.01"
                :max="Math.max(0.01, Number(form.price || 0) - 0.01)"
                :precision="2"
                :step="1"
                controls-position="right"
              />
            </div>
          </div>
          <div v-if="promotionEnabled" class="discount-preview">
            售价 ¥{{ formatPrice(form.salePrice) }}，优惠
            {{ formDiscountPercent }}%，每件节省 ¥{{ discountAmount }}
          </div>
        </el-form-item>
        <el-form-item label="库存" prop="stock">
          <el-input-number v-model="form.stock" :min="0" :max="9999999" controls-position="right" />
        </el-form-item>
        <el-form-item label="商品状态">
          <el-radio-group v-model="form.status">
            <el-radio-button :value="1">上架</el-radio-button>
            <el-radio-button :value="0">下架</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right" />
          <span class="field-help">数字越小越靠前</span>
        </el-form-item>
        <el-form-item label="商品描述">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="请输入商品详情" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProduct">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Picture, Plus, Search } from '@element-plus/icons-vue'
import {
  createProduct,
  deleteProduct as apiDeleteProduct,
  getCategoryList,
  getProductList,
  updateProduct,
  updateProductStatus
} from '@/api/productApi'
import type { ProductDTO } from '@/types'

interface ProductForm {
  id?: number
  categoryId: number | null
  name: string
  subtitle: string
  description: string
  imageUrl: string
  price: number
  salePrice: number | null
  stock: number
  status: number
  sort: number
}

const createDefaultForm = (): ProductForm => ({
  categoryId: null,
  name: '',
  subtitle: '',
  description: '',
  imageUrl: '',
  price: 0.01,
  salePrice: null,
  stock: 0,
  status: 0,
  sort: 0
})

const productList = ref<any[]>([])
const categories = ref<any[]>([])
const selection = ref<any[]>([])
const keyword = ref('')
const statusFilter = ref<number | null>(null)
const loading = ref(false)
const saving = ref(false)
const batchLoading = ref(false)
const statusLoadingId = ref<number | null>(null)
const editDialogVisible = ref(false)
const isEdit = ref(false)
const promotionEnabled = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<ProductForm>(createDefaultForm())

const categoryMap = computed(() => {
  return new Map(categories.value.map((category) => [category.id, category.name]))
})

const filteredProducts = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return productList.value.filter((product) => {
    const matchesKeyword = !search || String(product.name || '').toLowerCase().includes(search)
    const matchesStatus = statusFilter.value == null || product.status === statusFilter.value
    return matchesKeyword && matchesStatus
  })
})

const formDiscountPercent = computed(() => {
  if (!promotionEnabled.value || !form.price || !form.salePrice || form.salePrice >= form.price) return 0
  return Math.round((1 - Number(form.salePrice) / Number(form.price)) * 100)
})

const discountAmount = computed(() => {
  if (!form.salePrice || !form.price) return '0.00'
  return Math.max(0, Number(form.price) - Number(form.salePrice)).toFixed(2)
})

const validateSalePrice = (_rule: any, _value: any, callback: (error?: Error) => void) => {
  if (!promotionEnabled.value) {
    callback()
    return
  }
  if (!form.salePrice || form.salePrice <= 0) {
    callback(new Error('请输入优惠价'))
    return
  }
  if (form.salePrice >= form.price) {
    callback(new Error('优惠价必须低于原价'))
    return
  }
  callback()
}

const rules: FormRules = {
  categoryId: [{ required: true, message: '请选择商品分类', trigger: 'change' }],
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  price: [{ required: true, message: '请输入商品原价', trigger: 'change' }],
  stock: [{ required: true, message: '请输入商品库存', trigger: 'change' }],
  salePrice: [{ validator: validateSalePrice, trigger: 'change' }]
}

watch(promotionEnabled, (enabled) => {
  if (enabled && (!form.salePrice || form.salePrice <= 0 || form.salePrice >= form.price)) {
    form.salePrice = Number(Math.max(0.01, Number(form.price || 0) * 0.9).toFixed(2))
  }
  if (!enabled) {
    form.salePrice = null
  }
})

watch(() => form.price, (price) => {
  if (promotionEnabled.value && form.salePrice && form.salePrice >= price) {
    form.salePrice = Number(Math.max(0.01, Number(price) * 0.9).toFixed(2))
  }
})

const loadProducts = async () => {
  loading.value = true
  try {
    const res = await getProductList({ page: 1, size: 100 })
    productList.value = res.data?.list || []
  } catch (error: any) {
    ElMessage.error(error.message || '商品加载失败')
  } finally {
    loading.value = false
  }
}

const formatPrice = (price: number | string | undefined) => Number(price || 0).toFixed(2)

const categoryName = (categoryId: number) => categoryMap.value.get(categoryId) || `分类 ${categoryId}`

const discountPercent = (row: any) => {
  if (!row.salePrice || !row.price || Number(row.salePrice) >= Number(row.price)) return 0
  return Math.round((1 - Number(row.salePrice) / Number(row.price)) * 100)
}

const firstImage = (images: string) => {
  if (!images) return ''
  try {
    const parsed = JSON.parse(images)
    return Array.isArray(parsed) ? parsed[0] || '' : ''
  } catch {
    return images
  }
}

const openCreateDialog = () => {
  isEdit.value = false
  Object.assign(form, createDefaultForm())
  promotionEnabled.value = false
  editDialogVisible.value = true
}

const openEditDialog = (row: any) => {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    categoryId: row.categoryId,
    name: row.name || '',
    subtitle: row.subtitle || '',
    description: row.description || '',
    imageUrl: firstImage(row.images),
    price: Number(row.price || 0.01),
    salePrice: row.salePrice ? Number(row.salePrice) : null,
    stock: Number(row.stock || 0),
    status: Number(row.status ?? 0),
    sort: Number(row.sort || 0)
  })
  promotionEnabled.value = Boolean(row.salePrice)
  editDialogVisible.value = true
}

const toPayload = (): ProductDTO => ({
  categoryId: Number(form.categoryId),
  name: form.name.trim(),
  subtitle: form.subtitle.trim(),
  description: form.description.trim(),
  images: form.imageUrl.trim() ? JSON.stringify([form.imageUrl.trim()]) : '[]',
  price: Number(form.price),
  salePrice: promotionEnabled.value && form.salePrice ? Number(form.salePrice) : null,
  stock: Number(form.stock),
  status: Number(form.status),
  sort: Number(form.sort)
})

const saveProduct = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (isEdit.value && form.id) {
      await updateProduct(form.id, toPayload())
      ElMessage.success('商品和优惠价格已更新')
    } else {
      await createProduct(toPayload())
      ElMessage.success('商品已新增')
    }
    editDialogVisible.value = false
    await loadProducts()
  } catch (error: any) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const toggleStatus = async (row: any) => {
  const targetStatus = row.status === 1 ? 0 : 1
  statusLoadingId.value = row.id
  try {
    const res = await updateProductStatus(row.id, targetStatus)
    row.status = res.data
    ElMessage.success(targetStatus === 1 ? '商品已上架' : '商品已下架')
  } catch (error: any) {
    ElMessage.error(error.message || '状态修改失败')
  } finally {
    statusLoadingId.value = null
  }
}

const batchOffline = async () => {
  if (selection.value.length === 0) return
  batchLoading.value = true
  try {
    await Promise.all(selection.value.map((row) => updateProductStatus(row.id, 0)))
    ElMessage.success(`已下架 ${selection.value.length} 件商品`)
    selection.value = []
    await loadProducts()
  } catch (error: any) {
    ElMessage.error(error.message || '批量下架失败')
  } finally {
    batchLoading.value = false
  }
}

const deleteProduct = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定要删除“${row.name}”吗？`, '删除商品', { type: 'warning' })
  } catch {
    return
  }
  try {
    await apiDeleteProduct(row.id)
    productList.value = productList.value.filter((product) => product.id !== row.id)
    ElMessage.success('商品已删除')
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败')
  }
}

const handleSelection = (rows: any[]) => {
  selection.value = rows
}

onMounted(async () => {
  const categoryRes = await getCategoryList()
  categories.value = categoryRes.data || []
  await loadProducts()
})
</script>

<style scoped>
.product-manage-page {
  padding-bottom: 24px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.page-header h2 {
  margin-bottom: 5px;
  color: #263445;
  font-size: 23px;
}

.page-header p {
  color: #7d8996;
  font-size: 13px;
}

.toolbar-card {
  margin-bottom: 16px;
  border-radius: 8px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.search-input {
  width: 260px;
}

.status-select {
  width: 140px;
}

.product-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 3px 0;
}

.product-thumb {
  width: 58px;
  height: 58px;
  flex-shrink: 0;
  border-radius: 7px;
  background: #f5f7fa;
}

.image-placeholder {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  color: #b7c0ca;
  background: #f5f7fa;
}

.product-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.product-meta strong {
  color: #303b48;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-meta span {
  color: #909399;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
}

.current-price {
  color: #e4393c;
  font-size: 16px;
  font-weight: 700;
}

.original-price {
  margin-left: 7px;
  color: #a0a7b0;
  font-size: 12px;
  text-decoration: line-through;
}

.no-discount {
  color: #909399;
  font-size: 12px;
}

.price-setting {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}

.price-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.price-field > span,
.price-label-row > span {
  color: #606266;
  font-size: 13px;
}

.price-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.discount-preview {
  width: 100%;
  margin-top: 10px;
  padding: 9px 12px;
  border-radius: 6px;
  background: #fff6f2;
  color: #d84b25;
  font-size: 13px;
}

.field-help {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}

@media (max-width: 760px) {
  .page-header,
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .search-input,
  .status-select {
    width: 100%;
  }

  .price-setting {
    grid-template-columns: 1fr;
  }

  :deep(.el-dialog) {
    width: calc(100vw - 24px);
  }
}
</style>
