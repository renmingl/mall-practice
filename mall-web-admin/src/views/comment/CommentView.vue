<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { getAdminCommentPage, replyComment, updateCommentStatus, type AdminCommentRow } from '@/api/comment'

const loading = ref(false)
const list = ref<AdminCommentRow[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, keyword: '', status: undefined as number | undefined })

async function load() {
  loading.value = true
  try {
    const data = await getAdminCommentPage(query.page, query.size, query.keyword || undefined, query.status)
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

// ---------- 回复 ----------

const replyVisible = ref(false)
const replyFormRef = ref<FormInstance>()
const replyTarget = ref<AdminCommentRow | null>(null)
const replyForm = reactive({ reply: '' })

const replyRules = {
  reply: [{ required: true, message: '请输入回复内容', trigger: 'blur' }]
}

function openReply(row: AdminCommentRow) {
  replyTarget.value = row
  replyForm.reply = row.reply || ''
  replyVisible.value = true
}

async function submitReply() {
  await replyFormRef.value?.validate()
  await replyComment(replyTarget.value!.id, replyForm.reply)
  ElMessage.success('回复成功')
  replyVisible.value = false
  load()
}

// ---------- 隐藏/显示 ----------

async function onToggleStatus(row: AdminCommentRow) {
  const target = row.status === 1 ? 0 : 1
  await ElMessageBox.confirm(target === 0 ? '确定隐藏该评价？前端将不再展示' : '确定恢复显示该评价？', '提示', { type: 'warning' })
  await updateCommentStatus(row.id, target)
  ElMessage.success('操作成功')
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <el-form inline class="search-form">
        <el-form-item label="商品名称">
          <el-input v-model="query.keyword" placeholder="商品名称" clearable style="width: 200px" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px" @change="onSearch">
            <el-option label="正常" :value="1" />
            <el-option label="已隐藏" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="spuName" label="商品" min-width="150" show-overflow-tooltip />
        <el-table-column prop="orderSn" label="订单号" min-width="170" show-overflow-tooltip />
        <el-table-column label="评分" width="140">
          <template #default="{ row }">
            <el-rate :model-value="row.rating" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column label="评价内容" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content || '-' }}</template>
        </el-table-column>
        <el-table-column label="商家回复" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="row.reply">{{ row.reply }}<div class="reply-time">{{ row.replyTime?.replace('T', ' ').slice(0, 16) }}</div></template>
            <template v-else><span class="no-reply">未回复</span></template>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="85">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '正常' : '已隐藏' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评价时间" width="160">
          <template #default="{ row }">{{ row.createTime?.replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'product:comment:reply'" link type="primary" @click="openReply(row)">
              {{ row.reply ? '修改回复' : '回复' }}
            </el-button>
            <el-button v-perm="'product:comment:status'" link :type="row.status === 1 ? 'danger' : 'success'" @click="onToggleStatus(row)">
              {{ row.status === 1 ? '隐藏' : '显示' }}
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

    <!-- 回复弹窗 -->
    <el-dialog v-model="replyVisible" :title="`回复评价 #${replyTarget?.id}`" width="520px" destroy-on-close>
      <div v-if="replyTarget" class="comment-preview">
        <div class="preview-head">
          <el-rate :model-value="replyTarget.rating" disabled size="small" />
          <span class="preview-text">{{ replyTarget.content || '（无文字评价）' }}</span>
        </div>
      </div>
      <el-form ref="replyFormRef" :model="replyForm" :rules="replyRules" label-width="80px">
        <el-form-item label="回复内容" prop="reply">
          <el-input v-model="replyForm.reply" type="textarea" :rows="4" maxlength="200" show-word-limit placeholder="请输入回复内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReply">保存回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.reply-time {
  font-size: 12px;
  color: #909399;
}
.no-reply {
  color: #c0c4cc;
}
.comment-preview {
  margin-bottom: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
}
.preview-text {
  margin-left: 10px;
  color: #606266;
  font-size: 13px;
}
</style>
