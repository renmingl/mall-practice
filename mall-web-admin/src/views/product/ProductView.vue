<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteProduct, getCategoryTree, getProductPage, updateProductStatus, preloadProductCache, type CategoryNode, type Spu } from '@/api/product'

const router = useRouter()

// ---------- 查询 ----------

const loading = ref(false)
const list = ref<Spu[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, spuCode: '', name: '', categoryId: undefined as number | undefined, status: undefined as number | undefined })

// 分类筛选下拉（二级/三级分类拍平）
const categoryOptions = ref<{ id: number; name: string }[]>([])

function flattenCategories(nodes: CategoryNode[], prefix = '') {
  for (const node of nodes) {
    categoryOptions.value.push({ id: node.id!, name: prefix + node.name })
    if (node.children?.length) {
      flattenCategories(node.children, prefix + node.name + ' / ')
    }
  }
}

async function loadCategories() {
  categoryOptions.value = []
  flattenCategories(await getCategoryTree())
}

async function load() {
  loading.value = true
  try {
    const data = await getProductPage(query.page, query.size, {
      spuCode: query.spuCode || undefined,
      name: query.name || undefined,
      categoryId: query.categoryId,
      status: query.status
    })
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
  query.spuCode = ''
  query.name = ''
  query.categoryId = undefined
  query.status = undefined
  onSearch()
}

// ---------- 操作 ----------

async function onToggleStatus(row: Spu) {
  const next = row.status === 1 ? 0 : 1
  await updateProductStatus(row.id!, next)
  ElMessage.success(next === 1 ? '已上架' : '已下架')
  load()
}

async function onDelete(row: Spu) {
  await ElMessageBox.confirm(`确定删除商品「${row.name}」吗？删除后不可恢复！`, '提示', { type: 'warning' })
  await deleteProduct(row.id!)
  ElMessage.success('删除成功')
  load()
}

const preloading = ref(false)
async function onPreload() {
  preloading.value = true
  try {
    const count = await preloadProductCache()
    ElMessage.success(`预热完成，共 ${count} 个商品写入缓存`)
  } finally {
    preloading.value = false
  }
}

onMounted(() => {
  loadCategories()
  load()
})
</script>

<template>
  <div class="page">
    <!-- 搜索区 -->
    <el-card shadow="never" class="search-card">
      <el-form inline>
        <el-form-item label="商品编码">
          <el-input v-model="query.spuCode" placeholder="模糊搜索" clearable style="width: 150px" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="商品名称">
          <el-input v-model="query.name" placeholder="模糊搜索" clearable style="width: 180px" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.categoryId" placeholder="全部" clearable filterable style="width: 180px">
            <el-option v-for="c in categoryOptions" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 110px">
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
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
        <el-button v-perm="'product:spu:add'" type="primary" @click="router.push('/product/edit')">新增商品</el-button>
        <el-button v-perm="'product:spu:preload'" :loading="preloading" @click="onPreload">缓存预热</el-button>
      </div>
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="主图" width="80">
          <template #default="{ row }">
            <el-image v-if="row.mainPic" :src="row.mainPic" fit="cover" style="width: 48px; height: 48px; border-radius: 4px" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="spuCode" label="商品编码" width="130" />
        <el-table-column prop="name" label="商品名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="subtitle" label="副标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="sales" label="销量" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'product:spu:update'" link type="primary" @click="router.push(`/product/edit/${row.id}`)">编辑</el-button>
            <el-button v-perm="'product:spu:status'" link :type="row.status === 1 ? 'warning' : 'success'" @click="onToggleStatus(row)">
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button v-perm="'product:spu:delete'" link type="danger" @click="onDelete(row)">删除</el-button>
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
