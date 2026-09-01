<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import {
  deleteProduct,
  getProductPage,
  getSessionPage,
  preheatSession,
  saveProduct,
  saveSession,
  toggleProduct,
  toggleSession,
  type SeckillProductRow,
  type SeckillSessionRow
} from '@/api/seckill'
import { searchSkuList } from '@/api/product'

const route = useRoute()
// 菜单入口：/seckill/session 与 /seckill/product 指向本页，按路径定位 tab
const activeTab = ref(route.path.includes('product') ? 'product' : 'session')

// ---------- 场次管理（14.1） ----------

const sessionLoading = ref(false)
const sessionList = ref<SeckillSessionRow[]>([])
const sessionTotal = ref(0)
const sessionQuery = reactive({ page: 1, size: 10, keyword: '', status: undefined as number | undefined })

const PHASE_TEXT: Record<string, string> = { disabled: '已禁用', upcoming: '未开始', ongoing: '进行中', finished: '已结束' }

async function loadSessions() {
  sessionLoading.value = true
  try {
    const data = await getSessionPage(sessionQuery.page, sessionQuery.size, sessionQuery.keyword || undefined, sessionQuery.status)
    sessionList.value = data.records
    sessionTotal.value = data.total
  } finally {
    sessionLoading.value = false
  }
}

function onSessionSearch() {
  sessionQuery.page = 1
  loadSessions()
}

// 场次编辑弹窗
const sessionDialogVisible = ref(false)
const sessionFormRef = ref<FormInstance>()
const sessionForm = reactive({ id: undefined as number | undefined, name: '', startTime: '', endTime: '', status: 1 })

function openSessionDialog(row?: SeckillSessionRow) {
  Object.assign(sessionForm, {
    id: row?.id,
    name: row?.name ?? '',
    startTime: row ? row.startTime.replace('T', ' ') : '',
    endTime: row ? row.endTime.replace('T', ' ') : '',
    status: row?.status ?? 1
  })
  sessionDialogVisible.value = true
}

async function submitSession() {
  await sessionFormRef.value?.validate()
  if (!sessionForm.startTime || !sessionForm.endTime) {
    ElMessage.warning('请选择开始/结束时间')
    return
  }
  await saveSession({
    id: sessionForm.id,
    name: sessionForm.name,
    startTime: sessionForm.startTime.replace(' ', 'T'),
    endTime: sessionForm.endTime.replace(' ', 'T'),
    status: sessionForm.status
  })
  ElMessage.success(sessionForm.id ? '场次已更新' : '场次已创建')
  sessionDialogVisible.value = false
  loadSessions()
}

/** 启停场次 */
async function onToggleSession(row: SeckillSessionRow) {
  const next = row.status === 1 ? 0 : 1
  await toggleSession(row.id, next)
  ElMessage.success(next === 1 ? '已启用' : '已禁用')
  loadSessions()
}

/** 手动预热（14.3）：DB → Redis 秒杀库存 */
async function onPreheat(row: SeckillSessionRow) {
  await preheatSession(row.id)
  ElMessage.success(`场次「${row.name}」预热完成，Redis 秒杀库存已就绪`)
}

// ---------- 秒杀商品配置（14.2） ----------

const productLoading = ref(false)
const productList = ref<SeckillProductRow[]>([])
const productTotal = ref(0)
const productQuery = reactive({ page: 1, size: 10, sessionId: undefined as number | undefined, status: undefined as number | undefined })

async function loadProducts() {
  productLoading.value = true
  try {
    const data = await getProductPage(productQuery.page, productQuery.size, productQuery.sessionId, productQuery.status)
    productList.value = data.records
    productTotal.value = data.total
  } finally {
    productLoading.value = false
  }
}

function onProductSearch() {
  productQuery.page = 1
  loadProducts()
}

// SKU 搜索选择（商品配置弹窗内）
const skuOptions = ref<{ value: number; label: string }[]>([])
const skuKeyword = ref('')
const skuSearching = ref(false)

async function searchSku(keyword: string) {
  if (!keyword) return
  skuSearching.value = true
  try {
    const data = await searchSkuList(keyword)
    skuOptions.value = data.map((s) => ({ value: s.id, label: `[${s.skuCode}] ${s.spuName || ''} ${s.spec || ''}（库存 ${s.stock}）` }))
  } finally {
    skuSearching.value = false
  }
}

// 商品编辑弹窗
const productDialogVisible = ref(false)
const productFormRef = ref<FormInstance>()
const productForm = reactive({
  id: undefined as number | undefined,
  sessionId: undefined as number | undefined,
  skuId: undefined as number | undefined,
  seckillPrice: 0,
  seckillStock: 1,
  limitPerUser: 1,
  status: 1
})

function openProductDialog(row?: SeckillProductRow) {
  Object.assign(productForm, {
    id: row?.id,
    sessionId: row?.sessionId ?? productQuery.sessionId,
    skuId: row?.skuId,
    seckillPrice: row ? Number(row.seckillPrice) : 0,
    seckillStock: row?.seckillStock ?? 1,
    limitPerUser: row?.limitPerUser ?? 1,
    status: row?.status ?? 1
  })
  productDialogVisible.value = true
}

async function submitProduct() {
  await productFormRef.value?.validate()
  if (!productForm.sessionId) {
    ElMessage.warning('请先在场次管理创建场次')
    return
  }
  if (!productForm.skuId) {
    ElMessage.warning('请搜索并选择 SKU')
    return
  }
  await saveProduct({
    id: productForm.id,
    sessionId: productForm.sessionId,
    skuId: productForm.skuId,
    seckillPrice: productForm.seckillPrice,
    seckillStock: productForm.seckillStock,
    limitPerUser: productForm.limitPerUser,
    status: productForm.status
  })
  ElMessage.success(productForm.id ? '秒杀商品已更新' : '秒杀商品已配置')
  productDialogVisible.value = false
  loadProducts()
}

/** 启停秒杀商品 */
async function onToggleProduct(row: SeckillProductRow) {
  const next = row.status === 1 ? 0 : 1
  await toggleProduct(row.id, next)
  ElMessage.success(next === 1 ? '已启用' : '已禁用')
  loadProducts()
}

/** 删除秒杀商品（场次未开始才允许） */
async function onDeleteProduct(row: SeckillProductRow) {
  await ElMessageBox.confirm(`确定删除秒杀商品「${row.spuName || row.skuCode}」吗？`, '删除确认', { type: 'warning' })
  await deleteProduct(row.id)
  ElMessage.success('已删除')
  loadProducts()
}

function onTabChange(name: string | number) {
  if (name === 'product' && !productList.value.length) loadProducts()
}

onMounted(() => {
  loadSessions()
  if (activeTab.value === 'product') loadProducts()
})
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- 场次管理 -->
        <el-tab-pane label="秒杀场次" name="session">
          <el-form inline class="search-form">
            <el-form-item label="场次名称">
              <el-input v-model="sessionQuery.keyword" placeholder="关键字" clearable style="width: 180px" @keyup.enter="onSessionSearch" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="sessionQuery.status" placeholder="全部" clearable style="width: 120px" @change="onSessionSearch">
                <el-option label="启用" :value="1" />
                <el-option label="禁用" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="onSessionSearch">查询</el-button>
              <el-button v-perm="'seckill:session:add'" type="success" @click="openSessionDialog()">新增场次</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="sessionLoading" :data="sessionList" border stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="场次名称" min-width="160" />
            <el-table-column label="开始时间" width="170">
              <template #default="{ row }">{{ row.startTime?.replace('T', ' ') }}</template>
            </el-table-column>
            <el-table-column label="结束时间" width="170">
              <template #default="{ row }">{{ row.endTime?.replace('T', ' ') }}</template>
            </el-table-column>
            <el-table-column label="当前状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="阶段" width="100">
              <template #default="{ row }">
                <el-tag :type="row.phase === 'ongoing' ? 'danger' : row.phase === 'upcoming' ? 'warning' : 'info'">
                  {{ PHASE_TEXT[row.phase ?? ''] ?? '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button v-perm="'seckill:session:update'" link type="primary" @click="openSessionDialog(row)">编辑</el-button>
                <el-button v-perm="'seckill:session:status'" link :type="row.status === 1 ? 'warning' : 'success'" @click="onToggleSession(row)">
                  {{ row.status === 1 ? '禁用' : '启用' }}
                </el-button>
                <el-button v-perm="'seckill:session:preheat'" link type="danger" @click="onPreheat(row)">预热</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="sessionQuery.page"
            v-model:page-size="sessionQuery.size"
            :total="sessionTotal"
            layout="total, prev, pager, next, sizes"
            :page-sizes="[10, 20, 50]"
            class="pagination"
            @change="loadSessions"
          />
        </el-tab-pane>

        <!-- 秒杀商品配置 -->
        <el-tab-pane label="秒杀商品" name="product">
          <el-form inline class="search-form">
            <el-form-item label="场次">
              <el-select v-model="productQuery.sessionId" placeholder="全部" clearable style="width: 200px" @change="onProductSearch">
                <el-option v-for="s in sessionList" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="productQuery.status" placeholder="全部" clearable style="width: 120px" @change="onProductSearch">
                <el-option label="启用" :value="1" />
                <el-option label="禁用" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="onProductSearch">查询</el-button>
              <el-button v-perm="'seckill:product:add'" type="success" @click="openProductDialog()">配置秒杀商品</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="productLoading" :data="productList" border stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="spuName" label="商品名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="skuCode" label="SKU 编码" min-width="140" />
            <el-table-column prop="spec" label="规格" min-width="110" show-overflow-tooltip />
            <el-table-column label="原价" width="90">
              <template #default="{ row }">¥{{ Number(row.price ?? 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="秒杀价" width="90">
              <template #default="{ row }">
                <span class="sk-price">¥{{ Number(row.seckillPrice).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="秒杀库存" width="90">
              <template #default="{ row }">{{ row.seckillStock }}</template>
            </el-table-column>
            <el-table-column label="SKU 库存" width="90">
              <template #default="{ row }">{{ row.skuStock ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="limitPerUser" label="限购" width="70" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <el-button v-perm="'seckill:product:update'" link type="primary" @click="openProductDialog(row)">编辑</el-button>
                <el-button v-perm="'seckill:product:status'" link :type="row.status === 1 ? 'warning' : 'success'" @click="onToggleProduct(row)">
                  {{ row.status === 1 ? '禁用' : '启用' }}
                </el-button>
                <el-button v-perm="'seckill:product:delete'" link type="danger" @click="onDeleteProduct(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="productQuery.page"
            v-model:page-size="productQuery.size"
            :total="productTotal"
            layout="total, prev, pager, next, sizes"
            :page-sizes="[10, 20, 50]"
            class="pagination"
            @change="loadProducts"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 场次编辑弹窗 -->
    <el-dialog v-model="sessionDialogVisible" :title="sessionForm.id ? '编辑场次' : '新增场次'" width="480px" destroy-on-close>
      <el-form ref="sessionFormRef" :model="sessionForm" label-width="90px">
        <el-form-item label="场次名称" prop="name" :rules="[{ required: true, message: '请输入场次名称' }]">
          <el-input v-model="sessionForm.name" placeholder="如：9 月 1 日 10 点场" maxlength="64" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime" :rules="[{ required: true, message: '请选择开始时间' }]">
          <el-date-picker v-model="sessionForm.startTime" type="datetime" placeholder="选择开始时间" style="width: 100%" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime" :rules="[{ required: true, message: '请选择结束时间' }]">
          <el-date-picker v-model="sessionForm.endTime" type="datetime" placeholder="选择结束时间" style="width: 100%" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="sessionForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sessionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSession">保存</el-button>
      </template>
    </el-dialog>

    <!-- 秒杀商品编辑弹窗 -->
    <el-dialog v-model="productDialogVisible" :title="productForm.id ? '编辑秒杀商品' : '配置秒杀商品'" width="520px" destroy-on-close>
      <el-form ref="productFormRef" :model="productForm" label-width="90px">
        <el-form-item label="所属场次" prop="sessionId" :rules="[{ required: true, message: '请选择场次' }]">
          <el-select v-model="productForm.sessionId" placeholder="请选择场次" style="width: 100%">
            <el-option v-for="s in sessionList" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="SKU" prop="skuId" :rules="[{ required: true, message: '请选择 SKU' }]">
          <el-select
            v-model="productForm.skuId"
            filterable
            remote
            reserve-keyword
            :remote-method="searchSku"
            :loading="skuSearching"
            placeholder="输入 SKU 编码/商品名称搜索"
            style="width: 100%"
          >
            <el-option v-for="o in skuOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="秒杀价" prop="seckillPrice" :rules="[{ required: true, message: '请输入秒杀价' }]">
          <el-input-number v-model="productForm.seckillPrice" :min="0.01" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="秒杀库存" prop="seckillStock" :rules="[{ required: true, message: '请输入秒杀库存' }]">
          <el-input-number v-model="productForm.seckillStock" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="每人限购" prop="limitPerUser" :rules="[{ required: true, message: '请输入限购数量' }]">
          <el-input-number v-model="productForm.limitPerUser" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="productForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="productDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitProduct">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.search-form {
  margin-bottom: 8px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
.sk-price {
  color: #f56c6c;
  font-weight: 600;
}
</style>
