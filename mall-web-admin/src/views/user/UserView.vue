<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import {
  addUser,
  assignUserRoles,
  deleteUser,
  getRoleList,
  getUserPage,
  getUserRoles,
  resetUserPassword,
  updateUser,
  type AdminRole,
  type AdminUser
} from '@/api/auth'

// ---------- 查询 ----------

const loading = ref(false)
const list = ref<AdminUser[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, username: '', status: undefined as number | undefined })

async function load() {
  loading.value = true
  try {
    const data = await getUserPage(query.page, query.size, query.username || undefined, query.status)
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
  query.username = ''
  query.status = undefined
  onSearch()
}

// ---------- 新增/编辑 ----------

const dialogVisible = ref(false)
const editing = ref<AdminUser | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  id: undefined as number | undefined,
  username: '',
  password: '',
  nickname: '',
  phone: '',
  email: '',
  status: 1
})

function openAdd() {
  editing.value = null
  Object.assign(form, { id: undefined, username: '', password: '', nickname: '', phone: '', email: '', status: 1 })
  dialogVisible.value = true
}

function openEdit(row: AdminUser) {
  editing.value = row
  Object.assign(form, {
    id: row.id,
    username: row.username,
    password: '',
    nickname: row.nickname || '',
    phone: row.phone || '',
    email: row.email || '',
    status: row.status ?? 1
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  if (editing.value) {
    await updateUser({
      id: form.id,
      nickname: form.nickname,
      phone: form.phone,
      email: form.email,
      status: form.status
    })
    ElMessage.success('修改成功')
  } else {
    await addUser({
      username: form.username,
      password: form.password,
      nickname: form.nickname,
      phone: form.phone,
      email: form.email,
      status: form.status
    })
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  load()
}

async function onDelete(row: AdminUser) {
  await ElMessageBox.confirm(`确定删除用户「${row.username}」吗？`, '提示', { type: 'warning' })
  await deleteUser(row.id!)
  ElMessage.success('删除成功')
  load()
}

// ---------- 重置密码 ----------

const pwdDialogVisible = ref(false)
const pwdTarget = ref<AdminUser | null>(null)
const newPassword = ref('')

function openResetPwd(row: AdminUser) {
  pwdTarget.value = row
  newPassword.value = ''
  pwdDialogVisible.value = true
}

async function submitResetPwd() {
  if (!newPassword.value) {
    ElMessage.warning('请输入新密码')
    return
  }
  await resetUserPassword(pwdTarget.value!.id!, newPassword.value)
  pwdDialogVisible.value = false
  ElMessage.success('密码已重置')
}

// ---------- 分配角色 ----------

const roleDialogVisible = ref(false)
const roleTarget = ref<AdminUser | null>(null)
const roleOptions = ref<AdminRole[]>([])
const checkedRoleIds = ref<number[]>([])
const roleLoading = ref(false)

async function openAssignRoles(row: AdminUser) {
  roleTarget.value = row
  roleDialogVisible.value = true
  roleLoading.value = true
  try {
    const [roles, mine] = await Promise.all([getRoleList(), getUserRoles(row.id!)])
    roleOptions.value = roles
    checkedRoleIds.value = mine.map((r) => r.id!).filter((id) => id !== undefined)
  } finally {
    roleLoading.value = false
  }
}

async function submitRoles() {
  await assignUserRoles(roleTarget.value!.id!, checkedRoleIds.value)
  roleDialogVisible.value = false
  ElMessage.success('角色已更新')
}

onMounted(load)
</script>

<template>
  <div class="page">
    <!-- 搜索区 -->
    <el-card shadow="never" class="search-card">
      <el-form inline>
        <el-form-item label="用户名">
          <el-input v-model="query.username" placeholder="模糊搜索" clearable style="width: 180px" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
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
        <el-button v-perm="'system:user:add'" type="primary" @click="openAdd">新增用户</el-button>
      </div>
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="phone" label="手机号" min-width="130" />
        <el-table-column prop="email" label="邮箱" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginTime" label="最后登录" min-width="160" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'system:user:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-perm="'system:user:resetPwd'" link type="warning" @click="openResetPwd(row)">重置密码</el-button>
            <el-button v-perm="'system:user:assign'" link type="success" @click="openAssignRoles(row)">分配角色</el-button>
            <el-button v-perm="'system:user:delete'" link type="danger" @click="onDelete(row)">删除</el-button>
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
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑用户' : '新增用户'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item label="用户名" prop="username" :rules="[{ required: true, message: '请输入用户名' }]">
          <el-input v-model="form.username" :disabled="!!editing" placeholder="登录账号（唯一，不可修改）" />
        </el-form-item>
        <el-form-item v-if="!editing" label="密码" prop="password" :rules="[{ required: true, message: '请输入密码' }]">
          <el-input v-model="form.password" type="password" show-password placeholder="初始密码" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="姓名/昵称" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="选填" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="选填" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="pwdDialogVisible" title="重置密码" width="400px">
      <p>将重置用户「{{ pwdTarget?.username }}」的密码：</p>
      <el-input v-model="newPassword" type="password" show-password placeholder="请输入新密码" @keyup.enter="submitResetPwd" />
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResetPwd">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色弹窗 -->
    <el-dialog v-model="roleDialogVisible" :title="`分配角色：${roleTarget?.username}`" width="420px">
      <el-checkbox-group v-model="checkedRoleIds" class="role-group">
        <el-checkbox v-for="role in roleOptions" :key="role.id" :value="role.id!">
          {{ role.name }}（{{ role.code }}）
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleLoading" @click="submitRoles">确定</el-button>
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
.role-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 320px;
  overflow-y: auto;
}
</style>
