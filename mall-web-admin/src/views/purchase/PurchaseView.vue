<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import {
  auditPurchase,
  cancelPurchase,
  createPurchase,
  getPurchaseDetail,
  getPurchasePage,
  getStockPage,
  getSupplierPage,
  receivePurchase,
  type Purchase,
  type PurchaseItem,
  type StockRow,
  type Supplier
} from '@/api/product'

// ---------- 查询 ----------

const loading = ref(false)
const list = ref<Purchase[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, status: undefined as number | undefined, supplierId: undefined as number | undefined })

const STATUS_TEXT: Record<number, string> = { 0: '待审核', 1: '待收货', 2: '部分入库', 3: '已完成', 4: '已取消' }
const STATUS_TYPE: Record<number, 'warning' | 'primary' | 'success' | 'info' | 'danger'> = {
  0: 'warning',
  1: 'primary',
  2: 'success',
  3: 'success',
  4: 'info'
}

const supplierOptions = ref<Supplier[]>([])

async function loadSuppliers() {
  const data = await getSupplierPage(1, 100, undefined, 1)
  supplierOptions.value = data.records
}

async function load() {
  loading.value = true
  try {
    const data = await getPurchasePage(query.page, query.size, query.status, query.supplierId)
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
  query.status = undefined
  query.supplierId = undefined
  onSearch()
}

// ---------- 创建采购单 ----------

const createVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({ supplierId: undefined as number | undefined })
const items = ref<{ skuId?: number; quantity: number; purchasePrice?: number }[]>([])

const skuOptions = ref<StockRow[]>([])

function openCreate() {
  createForm.supplierId = undefined
  items.value = [{ skuId: undefined, quantity: 1, purchasePrice: undefined }]
  createVisible.value = true
  loadSkuOptions()
}

async function loadSkuOptions() {
  const data = await getStockPage(1, 100)
  skuOptions.value = data.records
}

function addItemRow() {
  items.value.push({ skuId: undefined, quantity: 1, purchasePrice: undefined })
}

function removeItemRow(index: number) {
  items.value.splice(index, 1)
}

async function submitCreate() {
  await createFormRef.value?.validate()
  if (!items.value.length) {
    ElMessage.warning('请至少添加一条采购明细')
    return
  }
  const invalid = items.value.find((i) => !i.skuId || !i.quantity || i.quantity <= 0 || !i.purchasePrice || i.purchasePrice <= 0)
  if (invalid) {
    ElMessage.warning('明细需选择 SKU 且数量、采购价大于 0')
    return
  }
  const id = await createPurchase({
    supplierId: createForm.supplierId!,
    items: items.value.map((i) => ({ skuId: i.skuId!, quantity: i.quantity, purchasePrice: i.purchasePrice! }))
  })
  ElMessage.success(`采购单已创建（单号 ID: ${id}），待审核`)
  createVisible.value = false
  load()
}

// ---------- 详情 + 入库 ----------

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<{ purchase: Purchase; supplierName?: string; items: PurchaseItem[] } | null>(null)
const receiveDialog = ref(false)
const receiveTarget = ref<PurchaseItem | null>(null)
const receiveQuantity = ref(1)

async function openDetail(row: Purchase) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await getPurchaseDetail(row.id!)
  } finally {
    detailLoading.value = false
  }
}

function openReceive(item: PurchaseItem) {
  receiveTarget.value = item
  receiveQuantity.value = item.quantity - (item.receivedQuantity ?? 0)
  receiveDialog.value = true
}

async function submitReceive() {
  if (!receiveTarget.value || receiveQuantity.value <= 0) {
    ElMessage.warning('入库数量必须大于 0')
    return
  }
  await receivePurchase({ itemId: receiveTarget.value.id!, quantity: receiveQuantity.value })
  ElMessage.success('入库成功，库存已增加并留痕')
  receiveDialog.value = false
  // 刷新详情 + 列表
  detail.value = await getPurchaseDetail(detail.value!.purchase.id!)
  load()
}

// ---------- 审核 / 取消 ----------

async function onAudit(row: Purchase, pass: boolean) {
  await ElMessageBox.confirm(pass ? '确定通过该采购单？通过后进入待收货，可分批入库。' : '确定驳回该采购单？驳回后状态置为已取消。', '提示', { type: 'warning' })
  await auditPurchase(row.id!, pass)
  ElMessage.success(pass ? '已审核通过' : '已驳回')
  load()
}

async function onCancel(row: Purchase) {
  await ElMessageBox.confirm('确定取消该采购单？已有入库记录将不可取消。', '提示', { type: 'warning' })
  await cancelPurchase(row.id!)
  ElMessage.success('已取消')
  load()
}

onMounted(() => {
  loadSuppliers()
  load()
})
</script>

<template>
  <div class="page">
    <!-- 搜索区 -->
    <el-card shadow="never" class="search-card">
      <el-form inline>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="(text, key) in STATUS_TEXT" :key="key" :label="text" :value="Number(key)" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="query.supplierId" placeholder="全部" clearable filterable style="width: 180px">
            <el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" />
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
        <el-button v-perm="'product:purchase:add'" type="primary" @click="openCreate">创建采购单</el-button>
      </div>
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="purchaseSn" label="采购单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="supplierId" label="供应商ID" width="90" />
        <el-table-column label="采购总额" width="120">
          <template #default="{ row }">¥{{ row.totalAmount?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="STATUS_TYPE[row.status ?? 0]">{{ STATUS_TEXT[row.status ?? 0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="auditBy" label="审核人" width="90" />
        <el-table-column prop="auditTime" label="审核时间" width="160" />
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <template v-if="row.status === 0">
              <el-button v-perm="'product:purchase:audit'" link type="success" @click="onAudit(row, true)">通过</el-button>
              <el-button v-perm="'product:purchase:audit'" link type="warning" @click="onAudit(row, false)">驳回</el-button>
            </template>
            <el-button v-if="row.status === 0 || row.status === 1" v-perm="'product:purchase:cancel'" link type="danger" @click="onCancel(row)">取消</el-button>
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

    <!-- 创建采购单弹窗 -->
    <el-dialog v-model="createVisible" title="创建采购单" width="680px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" label-width="80px">
        <el-form-item label="供应商" prop="supplierId" :rules="[{ required: true, message: '请选择供应商' }]">
          <el-select v-model="createForm.supplierId" placeholder="选择合作中的供应商" filterable style="width: 100%">
            <el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-table :data="items" border size="small">
        <el-table-column label="SKU" min-width="200">
          <template #default="{ row }">
            <el-select v-model="row.skuId" placeholder="选择 SKU" filterable style="width: 100%">
              <el-option v-for="sku in skuOptions" :key="sku.id" :label="`${sku.skuCode}（${sku.spec || '-'}）库存 ${sku.stock}`" :value="sku.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="1" :controls="false" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="采购价" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.purchasePrice" :min="0.01" :precision="2" :controls="false" style="width: 100%" placeholder="0.00" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeItemRow($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button class="add-item-btn" type="primary" plain @click="addItemRow">+ 添加明细</el-button>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="采购单详情" width="760px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="采购单号">{{ detail.purchase.purchaseSn }}</el-descriptions-item>
            <el-descriptions-item label="供应商">{{ detail.supplierName }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="STATUS_TYPE[detail.purchase.status ?? 0]">{{ STATUS_TEXT[detail.purchase.status ?? 0] }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="采购总额">¥{{ detail.purchase.totalAmount?.toFixed(2) }}</el-descriptions-item>
            <el-descriptions-item label="审核人">{{ detail.purchase.auditBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="审核时间">{{ detail.purchase.auditTime || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-table :data="detail.items" border size="small" class="item-table">
            <el-table-column prop="id" label="明细ID" width="80" />
            <el-table-column prop="skuId" label="SKU ID" width="80" />
            <el-table-column prop="quantity" label="采购数量" width="90" />
            <el-table-column prop="receivedQuantity" label="已入库" width="90" />
            <el-table-column label="采购价" width="110">
              <template #default="{ row }">¥{{ row.purchasePrice?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button
                  v-if="detail.purchase.status === 1 || detail.purchase.status === 2"
                  v-perm="'product:purchase:receive'"
                  link
                  type="primary"
                  :disabled="row.receivedQuantity >= row.quantity"
                  @click="openReceive(row)"
                >
                  {{ row.receivedQuantity >= row.quantity ? '已收满' : '入库' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-dialog>

    <!-- 入库弹窗 -->
    <el-dialog v-model="receiveDialog" title="分批入库" width="400px">
      <p v-if="receiveTarget">
        明细 #{{ receiveTarget.id }}：采购 {{ receiveTarget.quantity }}，已入库 {{ receiveTarget.receivedQuantity }}，本次入库
      </p>
      <el-input-number v-model="receiveQuantity" :min="1" :max="receiveTarget ? receiveTarget.quantity - (receiveTarget.receivedQuantity ?? 0) : 1" style="width: 100%" />
      <template #footer>
        <el-button @click="receiveDialog = false">取消</el-button>
        <el-button type="primary" @click="submitReceive">确认入库</el-button>
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
.add-item-btn {
  margin-top: 12px;
}
.item-table {
  margin-top: 16px;
}
</style>
