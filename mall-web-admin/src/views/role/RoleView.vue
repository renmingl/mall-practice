<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import {
  addRole,
  assignRoleMenus,
  deleteRole,
  getMenuTree,
  getRoleMenuIds,
  getRolePage,
  updateRole,
  type AdminMenu,
  type AdminRole
} from '@/api/auth'

// ---------- 查询 ----------

const loading = ref(false)
const list = ref<AdminRole[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, name: '' })

async function load() {
  loading.value = true
  try {
    const data = await getRolePage(query.page, query.size, query.name || undefined)
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
  onSearch()
}

// ---------- 新增/编辑 ----------

const dialogVisible = ref(false)
const editing = ref<AdminRole | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ id: undefined as number | undefined, name: '', code: '', description: '', status: 1 })

function openAdd() {
  editing.value = null
  Object.assign(form, { id: undefined, name: '', code: '', description: '', status: 1 })
  dialogVisible.value = true
}

function openEdit(row: AdminRole) {
  editing.value = row
  Object.assign(form, {
    id: row.id,
    name: row.name,
    code: row.code,
    description: row.description || '',
    status: row.status ?? 1
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  if (editing.value) {
    await updateRole({ id: form.id, name: form.name, description: form.description, status: form.status })
    ElMessage.success('修改成功')
  } else {
    await addRole({ name: form.name, code: form.code, description: form.description, status: form.status })
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  load()
}

async function onDelete(row: AdminRole) {
  await ElMessageBox.confirm(`确定删除角色「${row.name}」吗？已分配该角色的用户将无法登录。`, '提示', {
    type: 'warning'
  })
  await deleteRole(row.id!)
  ElMessage.success('删除成功')
  load()
}

// ---------- 分配菜单权限 ----------

const menuDialogVisible = ref(false)
const menuTarget = ref<AdminRole | null>(null)
const menuTree = ref<AdminMenu[]>([])
const checkedMenuIds = ref<number[]>([])
const menuLoading = ref(false)
const treeRef = ref()

// 树配置：label=name，勾选父子联动
const treeProps = { label: 'name', children: 'children' }

async function openAssignMenus(row: AdminRole) {
  menuTarget.value = row
  menuDialogVisible.value = true
  menuLoading.value = true
  try {
    const [tree, ids] = await Promise.all([getMenuTree(), getRoleMenuIds(row.id!)])
    menuTree.value = tree
    checkedMenuIds.value = ids
  } finally {
    menuLoading.value = false
  }
}

/** el-tree 勾选变化：同步已选节点 id（父节点勾选时子节点联动） */
function onTreeCheck(_node: unknown, ctx: { checkedKeys: Array<number | string> }) {
  checkedMenuIds.value = [...ctx.checkedKeys] as number[]
}

async function submitMenus() {
  // 仅提交叶子节点（勾选父节点时子节点已全选；避免父节点 id 覆盖子节点权限）
  const leafIds = checkedMenuIds.value.filter((id) => {
    const node = findMenu(menuTree.value, id)
    return !node?.children?.length
  })
  await assignRoleMenus(menuTarget.value!.id!, leafIds)
  menuDialogVisible.value = false
  ElMessage.success('菜单权限已更新')
}

/** 在菜单树中按 id 找节点 */
function findMenu(nodes: AdminMenu[], id: number): AdminMenu | null {
  for (const node of nodes) {
    if (node.id === id) return node
    if (node.children?.length) {
      const found = findMenu(node.children, id)
      if (found) return found
    }
  }
  return null
}

onMounted(load)
</script>

<template>
  <div class="page">
    <!-- 搜索区 -->
    <el-card shadow="never" class="search-card">
      <el-form inline>
        <el-form-item label="角色名称">
          <el-input v-model="query.name" placeholder="模糊搜索" clearable style="width: 180px" @keyup.enter="onSearch" />
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
        <el-button v-perm="'system:role:add'" type="primary" @click="openAdd">新增角色</el-button>
      </div>
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="角色名称" min-width="140" />
        <el-table-column prop="code" label="角色编码" min-width="140" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'system:role:assign'" link type="success" @click="openAssignMenus(row)">分配菜单</el-button>
            <el-button v-perm="'system:role:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-perm="'system:role:delete'" link type="danger" @click="onDelete(row)">删除</el-button>
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
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑角色' : '新增角色'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item label="角色名称" prop="name" :rules="[{ required: true, message: '请输入角色名称' }]">
          <el-input v-model="form.name" placeholder="如：运营专员" />
        </el-form-item>
        <el-form-item label="角色编码" prop="code" :rules="[{ required: true, message: '请输入角色编码' }]">
          <el-input v-model="form.code" :disabled="!!editing" placeholder="如：OPERATOR（编辑时不可修改）" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="选填" />
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

    <!-- 分配菜单权限弹窗 -->
    <el-dialog v-model="menuDialogVisible" :title="`分配菜单权限：${menuTarget?.name}`" width="420px">
      <el-tree
        ref="treeRef"
        v-loading="menuLoading"
        :data="menuTree"
        :props="treeProps"
        node-key="id"
        show-checkbox
        default-expand-all
        :default-checked-keys="checkedMenuIds"
        @check="onTreeCheck"
      />
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitMenus">确定</el-button>
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
