<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auditRefund, confirmReturnRefund, getAdminRefundPage, retryRefund, type AdminRefundRow } from '@/api/refund'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const list = ref<AdminRefundRow[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, orderSn: '', status: undefined as number | undefined })

const STATUS_TEXT = ['申请中', '审核通过', '退货中', '退款中', '已退款', '已拒绝']
const STATUS_TAG = ['warning', 'primary', 'primary', 'primary', 'success', 'danger']

async function load() {
  loading.value = true
  try {
    const data = await getAdminRefundPage(query.page, query.size, query.orderSn || undefined, query.status)
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

const STATUS_OPTIONS = STATUS_TEXT.map((text, idx) => ({ value: idx, label: text }))

/** 审核：仅退款通过即执行退款；退货退款通过后需确认退货 */
async function onAudit(row: AdminRefundRow, approved: boolean) {
  const action = approved ? '通过' : '拒绝'
  const tip = approved && row.refundType === 1 ? '通过后立即执行退款（联动回补库存/退券/扣回积分）' : ''
  await ElMessageBox.confirm(`确定${action}退款申请「${row.refundSn}」？${tip}`, '提示', { type: 'warning' })
  await auditRefund(row.id, approved, userStore.user?.username || 'admin')
  ElMessage.success(`已${action}`)
  load()
}

/** 确认退货：审核通过（status=1）且退货退款（refundType=2）时，确认收到退货后执行退款 */
async function onConfirmReturn(row: AdminRefundRow) {
  await ElMessageBox.confirm(`确认已收到退货「${row.refundSn}」？确认后立即执行退款`, '提示', { type: 'warning' })
  await confirmReturnRefund(row.id)
  ElMessage.success('已确认退货，退款执行中')
  load()
}

/** 重试执行退款：仅退款审核通过后执行失败/超时的补偿入口（退款单停留审核通过或退款中） */
async function onRetry(row: AdminRefundRow) {
  await ElMessageBox.confirm(`重新执行退款「${row.refundSn}」？将再次联动回补库存/退券/扣回积分`, '提示', { type: 'warning' })
  await retryRefund(row.id)
  ElMessage.success('已重新执行退款')
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <el-form inline class="search-form">
        <el-form-item label="订单号">
          <el-input v-model="query.orderSn" placeholder="订单号" clearable style="width: 220px" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px" @change="onSearch">
            <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="refundSn" label="退款单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="orderSn" label="订单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="类型" width="95">
          <template #default="{ row }">
            <el-tag :type="row.refundType === 2 ? 'warning' : 'info'">{{ row.refundType === 2 ? '退货退款' : '仅退款' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="退款金额" width="100">
          <template #default="{ row }">
            <span class="refund-amount">¥{{ Number(row.refundAmount).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-tag :type="(STATUS_TAG[row.status] as any) || 'info'">{{ STATUS_TEXT[row.status] || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="原因" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason || '-' }}</template>
        </el-table-column>
        <el-table-column label="退货物流" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="row.refundType === 2 && row.returnCompany">{{ row.returnCompany }} {{ row.returnSn }}</template>
            <template v-else>-</template>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="160">
          <template #default="{ row }">{{ row.applyTime?.replace('T', ' ').slice(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="审核人" width="90">
          <template #default="{ row }">{{ row.auditBy || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button v-perm="'refund:audit'" link type="success" @click="onAudit(row, true)">通过</el-button>
              <el-button v-perm="'refund:audit'" link type="danger" @click="onAudit(row, false)">拒绝</el-button>
            </template>
            <el-button v-else-if="row.status === 1 && row.refundType === 2" v-perm="'refund:confirmReturn'" link type="primary" @click="onConfirmReturn(row)">
              确认退货
            </el-button>
            <el-button v-else-if="row.status === 1 || row.status === 3" v-perm="'refund:audit'" link type="warning" @click="onRetry(row)">
              执行退款
            </el-button>
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
.refund-amount {
  color: #f56c6c;
  font-weight: 600;
}
</style>
