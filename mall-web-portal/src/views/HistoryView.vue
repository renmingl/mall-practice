<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getBrowseHistory } from '@/api/seckill'

const router = useRouter()

interface HistoryRow {
  spuId: number
  viewTime: number
  spuName?: string
  mainPic?: string
}

const list = ref<HistoryRow[]>([])
const loading = ref(false)

function fmtTime(ts: number) {
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function load() {
  loading.value = true
  try {
    list.value = await getBrowseHistory()
  } catch {
    list.value = []
    showToast('足迹加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="history-page">
    <van-nav-bar title="浏览足迹" fixed placeholder>
      <template #left>
        <span class="nav-link" @click="router.push('/')">首页</span>
      </template>
    </van-nav-bar>

    <van-loading v-if="loading" class="page-loading" />
    <van-empty v-else-if="!list.length" description="暂无浏览记录，去逛逛吧" />
    <van-card v-for="row in list" :key="row.spuId" class="history-card" :title="row.spuName || `SPU ${row.spuId}`" :thumb="row.mainPic" @click="router.push(`/product/${row.spuId}`)">
      <template #footer>
        <span class="view-time">浏览于 {{ fmtTime(row.viewTime) }}</span>
      </template>
    </van-card>
  </div>
</template>

<style scoped>
.history-page {
  max-width: 640px;
  margin: 0 auto;
}
.nav-link {
  color: #1989fa;
  cursor: pointer;
}
.page-loading {
  padding: 40px 0;
}
.history-card {
  margin-bottom: 8px;
}
.view-time {
  color: #969799;
  font-size: 12px;
}
</style>
