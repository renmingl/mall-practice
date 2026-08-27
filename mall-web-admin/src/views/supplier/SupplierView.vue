<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { addSupplier, deleteSupplier, getSupplierPage, updateSupplier, updateSupplierStatus, type Supplier } from '@/api/product'

// ---------- 查询 ----------

const loading = ref(false)
const list = ref<Supplier[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, name: '', status: undefined as number | undefined })

async function load() {
  loading.value = true
  try {
    const data = await getSupplierPage(query.page, query.size, query.name || undefined, query.status)
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
const editing = ref<Supplier | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  id: undefined as number | undefined,
  name: '',
  contact: '',
  phone: '',
  address: '',
  remark: '',
  status: 1
})

function openAdd() {
  editing.value = null
  Object.assign(form, { id: undefined, name: '', contact: '', phone: '', address: '', remark: '', status: 1 })
  dialogVisible.value = true
}

function openEdit(row: Supplier) {
  editing.value = row
  Object.assign(form, {
    id: row.id,
    name: row.name,
    contact: row.contact || '',
    phone: row.phone || '',
    address: row.address || '',
    remark: row.remark || '',
    status: row.status ?? 1
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  if (editing.value) {
    await updateSupplier({ id: form.id, name: form.name, contact: form.contact, phone: form.phone, address: form.address, remark: form.remark, status: form.status })
    ElMessage.success('修改成功')
  } else {
    await addSupplier({ name: form.name, contact: form.contact, phone: form.phone, address: form.address, remark: form.remark, status: form.status })
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  load()
}

async function onDelete(row: Supplier) {
  await ElMessageBox.confirm(`确定删除供应商「${row.name}」吗？`, '提示', { type: 'warning' })
  await deleteSupplier(row.id!)
  ElMessage.success('删除成功')
  load()
}

async function onToggleStatus(row: Supplier) {
  const next = row.status === 1 ? 0 : 1
  await updateSupplierStatus(row.id!, next)
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
        <el-form-item label="供应商名">
          <el-input v-model="query.name" placeholder="模糊搜索" clearable style="width: 180px" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="合作中" :value="1" />
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
        <el-button v-perm="'product:supplier:add'" type="primary" @click="openAdd">新增供应商</el-button>
      </div>
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="供应商名称" min-width="150" />
        <el-table-column prop="contact" label="联系人" width="110" />
        <el-table-column prop="phone" label="联系电话" width="130" />
        <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '合作中' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'product:supplier:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-perm="'product:supplier:status'" link :type="row.status === 1 ? 'warning' : 'success'" @click="onToggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button v-perm="'product:supplier:delete'" link type="danger" @click="onDelete(row)">删除</el-button>
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
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑供应商' : '新增供应商'" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-form-item label="供应商名" prop="name" :rules="[{ required: true, message: '请输入供应商名称' }]">
          <el-input v-model="form.name" placeholder="如：深圳华强电子" maxlength="64" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contact" placeholder="选填" maxlength="32" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" placeholder="选填" maxlength="20" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" placeholder="选填" maxlength="128" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">合作中</el-radio>
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
