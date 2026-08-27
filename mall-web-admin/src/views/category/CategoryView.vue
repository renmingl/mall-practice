<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { addCategory, deleteCategory, getCategoryTree, updateCategory, updateCategoryStatus, type CategoryNode } from '@/api/product'

const loading = ref(false)
const tree = ref<CategoryNode[]>([])

async function load() {
  loading.value = true
  try {
    tree.value = await getCategoryTree()
  } finally {
    loading.value = false
  }
}

// ---------- 新增/编辑 ----------

const dialogVisible = ref(false)
const editing = ref<CategoryNode | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  id: undefined as number | undefined,
  parentId: 0,
  name: '',
  icon: '',
  sort: 0,
  status: 1
})

/** 打开新增弹窗（parentId 为 0 表示一级分类） */
function openAdd(parentId = 0) {
  editing.value = null
  Object.assign(form, { id: undefined, parentId, name: '', icon: '', sort: 0, status: 1 })
  dialogVisible.value = true
}

function openEdit(row: CategoryNode) {
  editing.value = row
  Object.assign(form, {
    id: row.id,
    parentId: row.parentId,
    name: row.name,
    icon: row.icon || '',
    sort: row.sort ?? 0,
    status: row.status ?? 1
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  if (editing.value) {
    await updateCategory({ id: form.id, parentId: form.parentId, name: form.name, icon: form.icon, sort: form.sort, status: form.status })
    ElMessage.success('修改成功')
  } else {
    await addCategory({ parentId: form.parentId, name: form.name, icon: form.icon, sort: form.sort, status: form.status })
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  load()
}

async function onDelete(row: CategoryNode) {
  await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？`, '提示', { type: 'warning' })
  await deleteCategory(row.id!)
  ElMessage.success('删除成功')
  load()
}

async function onToggleStatus(row: CategoryNode) {
  const next = row.status === 1 ? 0 : 1
  await updateCategoryStatus(row.id!, next)
  ElMessage.success(next === 1 ? '已启用' : '已停用')
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-button v-perm="'product:category:add'" type="primary" @click="openAdd()">新增一级分类</el-button>
        <el-button v-perm="'product:category:list'" @click="load">刷新</el-button>
      </div>
      <el-table v-loading="loading" :data="tree" row-key="id" border :tree-props="{ children: 'children' }" default-expand-all>
        <el-table-column prop="name" label="分类名称" min-width="200" />
        <el-table-column prop="level" label="层级" width="80">
          <template #default="{ row }">
            <el-tag size="small">{{ ['', '一级', '二级', '三级'][row.level] || row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.level < 3" v-perm="'product:category:add'" link type="primary" @click="openAdd(row.id)">新增子分类</el-button>
            <el-button v-perm="'product:category:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-perm="'product:category:status'" link :type="row.status === 1 ? 'warning' : 'success'" @click="onToggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button v-perm="'product:category:delete'" link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑分类' : '新增分类'" width="440px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item v-if="form.parentId !== 0" label="父级分类">
          <el-input :model-value="form.parentId" disabled />
        </el-form-item>
        <el-form-item label="名称" prop="name" :rules="[{ required: true, message: '请输入分类名称' }]">
          <el-input v-model="form.name" placeholder="如：手机数码" maxlength="30" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="前端图标名（选填）" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}
</style>
