<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { addBrand, deleteBrand, getBrandPage, updateBrand, updateBrandStatus, type Brand } from '@/api/product'

// ---------- 查询 ----------

const loading = ref(false)
const list = ref<Brand[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, name: '', status: undefined as number | undefined })

async function load() {
  loading.value = true
  try {
    const data = await getBrandPage(query.page, query.size, query.name || undefined, query.status)
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  load()
}

function onReset() {
  query.name = ''
  query.status = undefined
  onSearch()
}

// ---------- 新增/编辑 ----------

const dialogVisible = ref(false)
const editing = ref<Brand | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  id: undefined as number | undefined,
  name: '',
  logo: '',
  description: '',
  sort: 0,
  status: 1
})

function openAdd() {
  editing.value = null
  Object.assign(form, { id: undefined, name: '', logo: '', description: '', sort: 0, status: 1 })
  dialogVisible.value = true
}

function openEdit(row: Brand) {
  editing.value = row
  Object.assign(form, {
    id: row.id,
    name: row.name,
    logo: row.logo || '',
    description: row.description || '',
    sort: row.sort ?? 0,
    status: row.status ?? 1
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  if (editing.value) {
    await updateBrand({ id: form.id, name: form.name, logo: form.logo, description: form.description, sort: form.sort, status: form.status })
    ElMessage.success('修改成功')
  } else {
    await addBrand({ name: form.name, logo: form.logo, description: form.description, sort: form.sort, status: form.status })
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  load()
}

async function onDelete(row: Brand) {
  await ElMessageBox.confirm(`确定删除品牌「${row.name}」吗？`, '提示', { type: 'warning' })
  await deleteBrand(row.id!)
  ElMessage.success('删除成功')
  load()
}

async function onToggleStatus(row: Brand) {
  const next = row.status === 1 ? 0 : 1
  await updateBrandStatus(row.id!, next)
  ElMessage.success(next === 1 ? '已启用' : '已停用')
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <!-- 搜索区 -->
    <el-card shadow="never" class="search-card">
      <el-form inline>
        <el-form-item label="品牌名">
          <el-input v-model="query.name" placeholder="模糊搜索" clearable style="width: 180px" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <div class="toolbar">
        <el-button v-perm="'product:brand:add'" type="primary" @click="openAdd">新增品牌</el-button>
      </div>
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="Logo" width="90">
          <template #default="{ row }">
            <el-image v-if="row.logo" :src="row.logo" fit="cover" style="width: 40px; height: 40px; border-radius: 4px" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="品牌名" min-width="140" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'product:brand:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-perm="'product:brand:status'" link :type="row.status === 1 ? 'warning' : 'success'" @click="onToggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button v-perm="'product:brand:delete'" link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next, sizes"
        :page-sizes="[10, 20, 50]"
        class="pagination"
        @change="load"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑品牌' : '新增品牌'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item label="品牌名" prop="name" :rules="[{ required: true, message: '请输入品牌名' }]">
          <el-input v-model="form.name" placeholder="如：华为、小米" maxlength="30" />
        </el-form-item>
        <el-form-item label="Logo">
          <el-input v-model="form.logo" placeholder="图片 URL（可先用上传接口获取）" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="选填" />
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
.search-card {
  margin-bottom: 16px;
}
.toolbar {
  margin-bottom: 12px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
