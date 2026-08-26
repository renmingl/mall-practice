<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addMenu,
  deleteMenu,
  getMenuTree,
  updateMenu,
  type AdminMenu
} from '@/api/auth'

// ---------- 查询（树形展示） ----------

const loading = ref(false)
const tree = ref<AdminMenu[]>([])

async function load() {
  loading.value = true
  try {
    tree.value = await getMenuTree()
  } finally {
    loading.value = false
  }
}

// ---------- 新增/编辑 ----------

const dialogVisible = ref(false)
const editing = ref<AdminMenu | null>(null)
const form = reactive({
  id: undefined as number | undefined,
  parentId: 0,
  name: '',
  type: 1,
  path: '',
  perms: '',
  icon: '',
  sort: 0,
  status: 1
})

// 父级下拉：包装一个「顶级菜单」根节点
const parentOptions = computed<AdminMenu[]>(() => [
  { id: 0, parentId: 0, name: '顶级菜单', type: 1, children: tree.value }
])

/** 父级候选禁用：按钮不可作父级；编辑时自身及其后代不可选（防止成环） */
function isParentDisabled(data: AdminMenu): boolean {
  if (data.type === 3) return true
  if (editing.value && data.id) {
    if (data.id === editing.value.id) return true
    if (hasNode(tree.value, editing.value.id!, data.id)) return true
  }
  return false
}

/** 判断 nodeId 是否为 ancestorId 的后代 */
function hasNode(nodes: AdminMenu[], ancestorId: number, nodeId: number): boolean {
  for (const node of nodes) {
    if (node.id === ancestorId) {
      return !!node.children?.some((c) => c.id === nodeId || isDescendant(c, nodeId))
    }
    if (node.children?.length && hasNode(node.children, ancestorId, nodeId)) return true
  }
  return false
}

function isDescendant(node: AdminMenu, nodeId: number): boolean {
  return node.children?.some((c) => c.id === nodeId || isDescendant(c, nodeId)) ?? false
}

function openAdd(parent?: AdminMenu) {
  editing.value = null
  Object.assign(form, {
    id: undefined,
    parentId: parent?.id ?? 0,
    name: '',
    type: parent?.type === 2 ? 3 : 1, // 菜单下新增默认按钮，其余默认目录
    path: '',
    perms: '',
    icon: '',
    sort: 0,
    status: 1
  })
  dialogVisible.value = true
}

function openEdit(row: AdminMenu) {
  editing.value = row
  Object.assign(form, {
    id: row.id,
    parentId: row.parentId ?? 0,
    name: row.name,
    type: row.type,
    path: row.path || '',
    perms: row.perms || '',
    icon: row.icon || '',
    sort: row.sort ?? 0,
    status: row.status ?? 1
  })
  dialogVisible.value = true
}

/** 在树中按 id 找节点 */
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

async function submitForm() {
  // 手动校验（类型相关字段为条件必填）
  if (!form.name.trim()) {
    ElMessage.warning('请输入菜单名称')
    return
  }
  if (form.type === 1 || form.type === 2) {
    if (!form.path.trim()) {
      ElMessage.warning('请输入路由路径')
      return
    }
    if (!form.path.startsWith('/')) {
      ElMessage.warning('路由路径需以 / 开头')
      return
    }
  }
  if (form.type === 3 && !form.perms.trim()) {
    ElMessage.warning('按钮类型必须填写权限标识')
    return
  }
  // 父级类型约束
  if (form.parentId !== 0) {
    const parent = findMenu(tree.value, form.parentId)
    if (parent?.type === 3) {
      ElMessage.warning('按钮不能作为父级菜单')
      return
    }
    if (parent?.type === 2 && form.type !== 3) {
      ElMessage.warning('菜单下只能新增按钮')
      return
    }
  }

  const payload = {
    id: form.id,
    parentId: form.parentId,
    name: form.name.trim(),
    type: form.type,
    path: form.path.trim() || undefined,
    perms: form.perms.trim() || undefined,
    icon: form.icon.trim() || undefined,
    sort: form.sort,
    status: form.status
  }
  if (editing.value) {
    await updateMenu(payload)
    ElMessage.success('修改成功')
  } else {
    await addMenu(payload)
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  load()
}

async function onDelete(row: AdminMenu) {
  await ElMessageBox.confirm(`确定删除「${row.name}」吗？`, '提示', { type: 'warning' })
  await deleteMenu(row.id!)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-button v-perm="'system:menu:add'" type="primary" @click="openAdd()">新增菜单</el-button>
        <span class="tip">类型：1 目录 / 2 菜单 / 3 按钮；按钮作为页面上的操作权限标识</span>
      </div>
      <el-table
        v-loading="loading"
        :data="tree"
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
        border
      >
        <el-table-column prop="name" label="菜单名称" min-width="200" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.type === 1 ? 'primary' : row.type === 2 ? 'success' : 'info'" size="small">
              {{ row.type === 1 ? '目录' : row.type === 2 ? '菜单' : '按钮' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="图标" width="110">
          <template #default="{ row }">{{ row.icon || '-' }}</template>
        </el-table-column>
        <el-table-column prop="path" label="路由路径" min-width="150">
          <template #default="{ row }">{{ row.path || '-' }}</template>
        </el-table-column>
        <el-table-column prop="perms" label="权限标识" min-width="170">
          <template #default="{ row }">{{ row.perms || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.type !== 3" v-perm="'system:menu:add'" link type="success" @click="openAdd(row)">
              新增子级
            </el-button>
            <el-button v-perm="'system:menu:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-perm="'system:menu:delete'" link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑菜单' : '新增菜单'" width="520px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="父级菜单">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            :disable-data="isParentDisabled"
            check-strictly
            default-expand-all
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单类型">
          <el-radio-group v-model="form.type">
            <el-radio :value="1">目录</el-radio>
            <el-radio :value="2">菜单</el-radio>
            <el-radio :value="3">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如：系统管理 / 用户管理 / 用户新增" />
        </el-form-item>
        <template v-if="form.type !== 3">
          <el-form-item label="路由路径" required>
            <el-input v-model="form.path" placeholder="以 / 开头，如 /system/user" />
          </el-form-item>
          <el-form-item label="权限标识">
            <el-input v-model="form.perms" placeholder="选填，如 system:user:list" />
          </el-form-item>
          <el-form-item label="图标">
            <el-input v-model="form.icon" placeholder="选填，如 setting / user / menu" />
          </el-form-item>
        </template>
        <el-form-item v-else label="权限标识" required>
          <el-input v-model="form.perms" placeholder="如 system:user:add（@PreAuthorize 校验）" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
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
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.tip {
  color: #909399;
  font-size: 12px;
}
</style>
