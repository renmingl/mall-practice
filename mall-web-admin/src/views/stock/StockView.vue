<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { checkStock, getStockLogs, getStockPage, getStockWarnings, type StockLog, type StockRow } from '@/api/product'

const activeTab = ref('stock')

// ---------- 实时库存 ----------

const loading = ref(false)
const list = ref<StockRow[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, keyword: '' })

async function load() {
  loading.value = true
  try {
    const data = await getStockPage(query.page, query.size, query.keyword || undefined)
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

// ---------- 盘点调整 ----------

const checkVisible = ref(false)
const checkFormRef = ref<FormInstance>()
const checkTarget = ref<StockRow | null>(null)
const checkForm = reactive({ stock: 0, remark: '' })

function openCheck(row: StockRow) {
  checkTarget.value = row
  Object.assign(checkForm, { stock: row.stock, remark: '' })
  checkVisible.value = true
}

async function submitCheck() {
  await checkFormRef.value?.validate()
  await checkStock({ skuId: checkTarget.value!.id, stock: checkForm.stock, remark: checkForm.remark })
  ElMessage.success('盘点完成，差额已记流水（change_type=7）')
  checkVisible.value = false
  load()
  loadWarnings()
}

// ---------- 库存流水 ----------

const logLoading = ref(false)
const logList = ref<StockLog[]>([])
const logTotal = ref(0)
const logQuery = reactive({ page: 1, size: 10, skuId: undefined as number | undefined })

const CHANGE_TYPE: Record<number, { text: string; type: 'danger' | 'success' | 'warning' | 'primary' | 'info' }> = {
  1: { text: '下单扣减', type: 'danger' },
  2: { text: '取消回补', type: 'success' },
  3: { text: '退款回补', type: 'success' },
  4: { text: '秒杀扣减', type: 'danger' },
  5: { text: '采购入库', type: 'primary' },
  6: { text: '退货入库', type: 'success' },
  7: { text: '盘点调整', type: 'warning' },
  8: { text: '人工调整', type: 'warning' },
  9: { text: '秒杀回补', type: 'success' }
}

async function loadLogs() {
  logLoading.value = true
  try {
    const data = await getStockLogs(logQuery.skuId, logQuery.page, logQuery.size)
    logList.value = data.records
    logTotal.value = data.total
  } finally {
    logLoading.value = false
  }
}

// ---------- 预警 ----------

const warningLoading = ref(false)
const warningList = ref<{ id: number; skuCode: string; spuId: number; stock: number; lowStock: number }[]>([])

async function loadWarnings() {
  warningLoading.value = true
  try {
    warningList.value = await getStockWarnings()
  } finally {
    warningLoading.value = false
  }
}

function onTabChange(name: string | number) {
  if (name === 'logs') loadLogs()
  if (name === 'warning') loadWarnings()
}

onMounted(() => {
  load()
  loadWarnings()
})
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- 实时库存 -->
        <el-tab-pane label="实时库存" name="stock">
          <el-form inline class="search-form">
            <el-form-item label="关键词">
              <el-input v-model="query.keyword" placeholder="SKU 编码 / 商品名称" clearable style="width: 220px" @keyup.enter="onSearch" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="onSearch">查询</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="loading" :data="list" border stripe>
            <el-table-column prop="id" label="SKU ID" width="80" />
            <el-table-column prop="skuCode" label="SKU 编码" min-width="150" />
            <el-table-column prop="spuName" label="商品名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="spec" label="规格" min-width="120" show-overflow-tooltip />
            <el-table-column label="售价" width="100">
              <template #default="{ row }">¥{{ row.price?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="当前库存" width="100">
              <template #default="{ row }">
                <span :class="{ 'stock-warning': row.warning }">{{ row.stock }}</span>
              </template>
            </el-table-column>
            <el-table-column label="预警阈值" width="100">
              <template #default="{ row }">{{ row.lowStock ?? 10 }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updateTime" label="更新时间" width="160" />
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button v-perm="'product:stock:check'" link type="primary" @click="openCheck(row)">盘点</el-button>
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
        </el-tab-pane>

        <!-- 库存流水 -->
        <el-tab-pane label="库存流水" name="logs">
          <el-table v-loading="logLoading" :data="logList" border stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="skuId" label="SKU ID" width="90" />
            <el-table-column prop="bizSn" label="业务单号" min-width="170" show-overflow-tooltip />
            <el-table-column label="变动类型" width="110">
              <template #default="{ row }">
                <el-tag :type="CHANGE_TYPE[row.changeType]?.type || 'info'">{{ CHANGE_TYPE[row.changeType]?.text || row.changeType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="变动数量" width="100">
              <template #default="{ row }">
                <span :class="row.changeCount >= 0 ? 'change-in' : 'change-out'">
                  {{ row.changeCount >= 0 ? '+' : '' }}{{ row.changeCount }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="stockBefore" label="变动前" width="90" />
            <el-table-column prop="stockAfter" label="变动后" width="90" />
            <el-table-column prop="createTime" label="时间" width="170" />
          </el-table>
          <el-pagination
            v-model:current-page="logQuery.page"
            v-model:page-size="logQuery.size"
            :total="logTotal"
            layout="total, prev, pager, next, sizes"
            :page-sizes="[10, 20, 50]"
            class="pagination"
            @change="loadLogs"
          />
        </el-tab-pane>

        <!-- 库存预警 -->
        <el-tab-pane label="库存预警" name="warning">
          <el-alert
            :title="warningList.length ? `当前 ${warningList.length} 个 SKU 低于预警阈值` : '库存充足，无预警'"
            :type="warningList.length ? 'warning' : 'success'"
            :closable="false"
            class="warning-alert"
          />
          <el-table v-loading="warningLoading" :data="warningList" border stripe class="warning-table">
            <el-table-column prop="id" label="SKU ID" width="90" />
            <el-table-column prop="skuCode" label="SKU 编码" min-width="170" />
            <el-table-column prop="spuId" label="SPU ID" width="90" />
            <el-table-column label="当前库存" width="110">
              <template #default="{ row }">
                <el-tag type="danger">{{ row.stock }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lowStock" label="预警阈值" width="110" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 盘点弹窗 -->
    <el-dialog v-model="checkVisible" title="库存盘点调整" width="440px" destroy-on-close>
      <p v-if="checkTarget" class="check-tip">
        {{ checkTarget.skuCode }}（{{ checkTarget.spec || '-' }}），当前库存 {{ checkTarget.stock }}
      </p>
      <el-form ref="checkFormRef" :model="checkForm" label-width="80px">
        <el-form-item label="实际库存" prop="stock" :rules="[{ required: true, message: '请输入盘点后的实际库存' }]">
          <el-input-number v-model="checkForm.stock" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="checkForm.remark" placeholder="盘点原因（选填）" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCheck">确认盘点</el-button>
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
.stock-warning {
  color: #f56c6c;
  font-weight: 600;
}
.change-in {
  color: #67c23a;
}
.change-out {
  color: #f56c6c;
}
.warning-alert {
  margin-bottom: 16px;
}
.warning-table {
  margin-top: 4px;
}
.check-tip {
  margin: 0 0 16px;
  color: #666;
}
</style>
