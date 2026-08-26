<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { showToast } from 'vant'
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
    showToast('网关链路验证通过')
  } catch {
    showToast('网关链路验证失败：请确认 gateway(8080) 与 auth(8100) 已启动')
  } finally {
    loading.value = false
  }
}

onMounted(verify)
</script>

<template>
  <div class="home">
    <h1 class="title">mall-web-portal 前台商城（阶段 0 骨架）</h1>
    <van-card class="card">
      <template #title>骨架链路验证</template>
      <template #desc>
        <p>后端验证接口：<code>/api/common/ping</code>、<code>/api/common/trace</code>（经网关 → mall-auth）</p>
        <van-button type="primary" size="small" :loading="loading" @click="verify">重新验证</van-button>
        <p class="row">ping 响应：<code>{{ result || '未请求' }}</code></p>
        <p class="row">traceId（请求头 X-Trace-Id）：<code>{{ appStore.traceId || '未获取' }}</code></p>
      </template>
    </van-card>
  </div>
</template>

<style scoped>
.home {
  max-width: 640px;
  margin: 48px auto;
  padding: 0 16px;
}
.title {
  text-align: center;
  margin-bottom: 24px;
}
.row {
  margin-top: 12px;
  word-break: break-all;
}
</style>
