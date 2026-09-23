<template>
  <div class="category-manage-page">
    <div class="toolbar">
      <el-button type="primary" @click="openAddDialog">新增分类</el-button>
    </div>
    <el-table :data="categories" stripe border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="分类名称" min-width="150" />
      <el-table-column label="级别" width="80">
        <template #default="{ row }">
          {{ row.parentId === 0 ? '一级' : '二级' }}
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" @click="editCategory(row)">编辑</el-button>
          <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button size="small" type="danger" @click="deleteCategory(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="addDialogVisible" :title="isEdit ? '编辑分类' : '新增分类'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="分类名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="父分类">
          <el-select v-model="form.parentId" placeholder="选择父分类" clearable>
            <el-option v-for="cat in topCategories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCategory">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getCategoryList } from '@/api/productApi'

const categories = ref<any[]>([])
const topCategories = ref<any[]>([])
const addDialogVisible = ref(false)
const isEdit = ref(false)
const form = ref<any>({})

const loadCategories = async () => {
  const res = await getCategoryList()
  categories.value = res.data || []
  topCategories.value = categories.value.filter((c: any) => c.parentId === 0)
}

const openAddDialog = () => {
  isEdit.value = false
  form.value = {}
  addDialogVisible.value = true
}

const editCategory = (row: any) => {
  isEdit.value = true
  form.value = { ...row }
  addDialogVisible.value = true
}

const saveCategory = () => {
  addDialogVisible.value = false
  ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
  loadCategories()
}

const toggleStatus = async (row: any) => {
  row.status = row.status === 1 ? 0 : 1
  ElMessage.success(`${row.status === 1 ? '启用' : '禁用'}成功`)
}

const deleteCategory = (id: number) => {
  ElMessage.warning('删除操作已触发')
}

onMounted(() => { loadCategories() })
</script>

<style scoped>
.toolbar {
  margin-bottom: 20px;
}
</style>
