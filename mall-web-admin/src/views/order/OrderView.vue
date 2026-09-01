<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { deliverOrder, getAdminOrderPage, type AdminOrderItemRow, type AdminOrderPageRow } from '@/api/order'

// ---------- 列表 ----------

const loading = ref(false)
const list = ref<AdminOrderPageRow[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, orderSn: '', status: undefined as number | undefined })

const STATUS_TEXT = ['待付款', '待发货', '待收货', '已完成', '已取消', '已退款']
const STATUS_TAG = ['warning', 'primary', 'success', 'success', 'info', 'danger']
const PAY_TYPE = ['', '支付宝', '微信支付']

async function load() {
  loading.value = true
  try {
    const data = await getAdminOrderPage(query.page, query.size, query.orderSn || undefined, query.status)
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 商品件数合计（展开行展示） */
function itemCount(items: AdminOrderItemRow[]) {
  return items.reduce((s, i) => s + i.quantity, 0)
}

function onSearch() {
  query.page = 1
  load()
}

/** 订单状态筛选 options（含全部） */
const STATUS_OPTIONS = STATUS_TEXT.map((text, idx) => ({ value: idx, label: text }))

// ---------- 发货 ----------

const deliverVisible = ref(false)
const deliverFormRef = ref<FormInstance>()
const deliverTarget = ref<AdminOrderPageRow | null>(null)
const deliverForm = reactive({ company: '', sn: '' })

const deliverRules = {
  company: [{ required: true, message: '请输入物流公司', trigger: 'blur' }],
  sn: [{ required: true, message: '请输入物流单号', trigger: 'blur' }]
}

function openDeliver(row: AdminOrderPageRow) {
  deliverTarget.value = row
  Object.assign(deliverForm, { company: '', sn: '' })
  deliverVisible.value = true
}

async function submitDeliver() {
  await deliverFormRef.value?.validate()
  await deliverOrder(deliverTarget.value!.order.id, deliverForm.company, deliverForm.sn)
  ElMessage.success('发货成功')
  deliverVisible.value = false
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

      <el-table v-loading="loading" :data="list" border stripe row-key="orderId">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-body">
              <el-table :data="row.items" size="small" border>
                <el-table-column prop="skuCode" label="SKU 编码" width="140" />
                <el-table-column prop="spuName" label="商品名称" min-width="180" show-overflow-tooltip />
                <el-table-column prop="spec" label="规格" width="120" show-overflow-tooltip />
                <el-table-column label="单价" width="90">
                  <template #default="{ row: item }">¥{{ Number(item.price).toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="quantity" label="数量" width="70" />
                <el-table-column label="小计" width="100">
                  <template #default="{ row: item }">¥{{ Number(item.subtotal).toFixed(2) }}</template>
                </el-table-column>
              </el-table>
              <div class="expand-meta">
                收货人：{{ row.order.receiverName }}（{{ row.order.receiverPhone }}）｜地址：{{ row.order.receiverAddress }}
                <span v-if="row.order.remark">｜备注：{{ row.order.remark }}</span>
                <span v-if="row.order.deliveryCompany">｜物流：{{ row.order.deliveryCompany }} {{ row.order.deliverySn }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="order.orderSn" label="订单号" min-width="180" show-overflow-tooltip />
        <el-table-column label="商品件数" width="90">
          <template #default="{ row }">{{ itemCount(row.items) }}</template>
        </el-table-column>
        <el-table-column label="商品金额" width="100">
          <template #default="{ row }">¥{{ Number(row.order.totalAmount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="优惠" width="100">
          <template #default="{ row }">-¥{{ Number(row.order.couponAmount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="实付" width="100">
          <template #default="{ row }">
            <span class="pay-amount">¥{{ Number(row.order.payAmount).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="支付方式" width="90">
          <template #default="{ row }">{{ PAY_TYPE[row.order.payType] || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="(STATUS_TAG[row.order.status] as any) || 'info'">{{ STATUS_TEXT[row.order.status] || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="160">
          <template #default="{ row }">{{ row.order.createTime?.replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.order.status === 1" v-perm="'order:deliver'" link type="primary" @click="openDeliver(row)">发货</el-button>
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

    <!-- 发货弹窗 -->
    <el-dialog v-model="deliverVisible" title="订单发货" width="480px" destroy-on-close>
      <el-alert
        :title="`订单 ${deliverTarget?.order.orderSn}（¥${Number(deliverTarget?.order.payAmount || 0).toFixed(2)}）`"
        type="info"
        :closable="false"
        class="deliver-alert"
      />
      <el-form ref="deliverFormRef" :model="deliverForm" :rules="deliverRules" label-width="90px" class="deliver-form">
        <el-form-item label="物流公司" prop="company">
          <el-input v-model="deliverForm.company" placeholder="如：顺丰速运" maxlength="30" />
        </el-form-item>
        <el-form-item label="物流单号" prop="sn">
          <el-input v-model="deliverForm.sn" placeholder="快递单号" maxlength="40" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deliverVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDeliver">确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.expand-body {
  padding: 8px 16px;
}
.expand-meta {
  margin-top: 8px;
  font-size: 13px;
  color: #606266;
}
.pay-amount {
  color: #f56c6c;
  font-weight: 600;
}
.deliver-alert {
  margin-bottom: 16px;
}
.deliver-form {
  margin-top: 4px;
}
</style>
