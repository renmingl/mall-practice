<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ping, trace } from '@/api/ping'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
const result = ref('')
const loading = ref(false)

// 骨架验证：/api/common/ping + /api/common/trace，验证网关路由与 traceId 全链路透传
async function verify() {
  loading.value = true
  try {
    result.value = await ping()
    appStore.setTraceId(await trace())
    ElMessage.success('网关链路验证通过')
  } catch {
    ElMessage.error('网关链路验证失败：请确认 gateway(8080) 与 auth(8100) 已启动')
  } finally {
    loading.value = false
  }
}

onMounted(verify)
</script>

<template>
  <div class="home">
    <h1>mall-web-admin 管理后台（阶段 0 骨架）</h1>
    <el-card class="card">
      <template #header>骨架链路验证</template>
      <p>后端验证接口：<code>/api/common/ping</code>、<code>/api/common/trace</code>（经网关 → mall-auth）</p>
      <el-button type="primary" :loading="loading" @click="verify">重新验证</el-button>
      <el-divider />
      <p>ping 响应：<code>{{ result || '未请求' }}</code></p>
      <p>traceId（请求头 X-Trace-Id）：<code>{{ appStore.traceId || '未获取' }}</code></p>
    </el-card>
  </div>
</template>

<style scoped>
.home {
  max-width: 720px;
  margin: 48px auto;
  padding: 0 16px;
}
.card p {
  line-height: 1.8;
}
</style>
