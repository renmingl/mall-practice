<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import {
  getCouponTemplatePage,
  saveCouponTemplate,
  updateCouponTemplateStatus,
  type CouponTemplate,
  type CouponSavePayload
} from '@/api/coupon'

// ---------- 列表 ----------

const loading = ref(false)
const list = ref<CouponTemplate[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, name: '', status: undefined as number | undefined })

async function load() {
  loading.value = true
  try {
    const data = await getCouponTemplatePage(query.page, query.size, query.name || undefined, query.status)
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

/** 面值展示：满减券 ¥金额；折扣券 折扣率→折 */
function amountText(row: CouponTemplate) {
  return row.type === 2 ? `${(Number(row.amount) * 10).toFixed(1).replace(/\.0$/, '')} 折` : `¥${Number(row.amount).toFixed(2)}`
}

// ---------- 新增/编辑 ----------

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const editing = ref<CouponTemplate | null>(null)
const form = reactive({
  name: '',
  type: 1,
  amount: 0,
  threshold: 0,
  totalCount: 100,
  perLimit: 1,
  timeRange: [] as string[]
})

const rules = {
  name: [{ required: true, message: '请输入券名称', trigger: 'blur' }],
  amount: [{ required: true, message: '请输入面值', trigger: 'blur' }],
  totalCount: [{ required: true, message: '请输入发行总量', trigger: 'blur' }],
  perLimit: [{ required: true, message: '请输入每人限领', trigger: 'blur' }],
  timeRange: [{ required: true, message: '请选择有效期', trigger: 'change' }]
}

function openCreate() {
  editing.value = null
  Object.assign(form, { name: '', type: 1, amount: 0, threshold: 0, totalCount: 100, perLimit: 1, timeRange: [] })
  dialogVisible.value = true
}

function openEdit(row: CouponTemplate) {
  editing.value = row
  Object.assign(form, {
    name: row.name,
    type: row.type,
    amount: Number(row.amount),
    threshold: Number(row.threshold),
    totalCount: row.totalCount,
    perLimit: row.perLimit,
    timeRange: [row.useStartTime, row.useEndTime]
  })
  dialogVisible.value = true
}

async function submit() {
  await formRef.value?.validate()
  const payload: CouponSavePayload = {
    id: editing.value?.id,
    name: form.name,
    type: form.type,
    amount: form.amount,
    threshold: form.threshold,
    totalCount: form.totalCount,
    perLimit: form.perLimit,
    useStartTime: form.timeRange[0],
    useEndTime: form.timeRange[1]
  }
  await saveCouponTemplate(payload)
  ElMessage.success(editing.value ? '修改成功' : '新增成功')
  dialogVisible.value = false
  load()
}

// ---------- 启停 ----------

async function onToggleStatus(row: CouponTemplate) {
  const target = row.status === 1 ? 0 : 1
  await ElMessageBox.confirm(
    target === 0 ? `确定结束券「${row.name}」？结束后不可再领取` : `确定重新启用券「${row.name}」？`,
    '提示',
    { type: 'warning' }
  )
  await updateCouponTemplateStatus(row.id, target)
  ElMessage.success(target === 0 ? '已结束' : '已启用')
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <el-form inline class="search-form">
        <el-form-item label="券名称">
          <el-input v-model="query.name" placeholder="优惠券名称" clearable style="width: 200px" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px" @change="onSearch">
            <el-option label="进行中" :value="1" />
            <el-option label="已结束" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button v-perm="'coupon:template:add'" type="success" @click="openCreate">新增模板</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="券名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.type === 1 ? 'danger' : 'warning'">{{ row.type === 1 ? '满减券' : '折扣券' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="面值" width="100">
          <template #default="{ row }">{{ amountText(row) }}</template>
        </el-table-column>
        <el-table-column label="门槛" width="100">
          <template #default="{ row }">{{ Number(row.threshold) > 0 ? `满 ¥${Number(row.threshold).toFixed(2)}` : '无门槛' }}</template>
        </el-table-column>
        <el-table-column label="发行/已领" width="110">
          <template #default="{ row }">{{ row.totalCount }} / {{ row.receivedCount }}</template>
        </el-table-column>
        <el-table-column label="每人限领" width="90">
          <template #default="{ row }">{{ row.perLimit }} 张</template>
        </el-table-column>
        <el-table-column label="有效期" min-width="200">
          <template #default="{ row }">{{ row.useStartTime?.replace('T', ' ').slice(0, 16) }} ~ {{ row.useEndTime?.replace('T', ' ').slice(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '进行中' : '已结束' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'coupon:template:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-perm="'coupon:template:status'" link :type="row.status === 1 ? 'danger' : 'success'" @click="onToggleStatus(row)">
              {{ row.status === 1 ? '结束' : '启用' }}
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑优惠券模板' : '新增优惠券模板'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="券名称" prop="name">
          <el-input v-model="form.name" placeholder="如：满 100 减 20" maxlength="50" />
        </el-form-item>
        <el-form-item label="券类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :value="1">满减券</el-radio>
            <el-radio :value="2">折扣券</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="form.type === 1 ? '抵扣金额' : '折扣率'" prop="amount">
          <el-input-number v-model="form.amount" :min="0.01" :max="form.type === 1 ? 99999 : 0.99" :precision="form.type === 1 ? 2 : 2" :step="form.type === 1 ? 10 : 0.05" style="width: 220px" />
          <span class="form-tip">{{ form.type === 1 ? '元（满减金额）' : '如 0.85 表示 8.5 折' }}</span>
        </el-form-item>
        <el-form-item label="使用门槛">
          <el-input-number v-model="form.threshold" :min="0" :max="999999" :precision="2" style="width: 220px" />
          <span class="form-tip">满多少元可用（0 为无门槛）</span>
        </el-form-item>
        <el-form-item label="发行总量" prop="totalCount">
          <el-input-number v-model="form.totalCount" :min="1" :max="1000000" style="width: 220px" />
        </el-form-item>
        <el-form-item label="每人限领" prop="perLimit">
          <el-input-number v-model="form.perLimit" :min="1" :max="99" style="width: 220px" />
        </el-form-item>
        <el-form-item label="有效期" prop="timeRange">
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            start-placeholder="生效时间"
            end-placeholder="失效时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.form-tip {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
</style>
