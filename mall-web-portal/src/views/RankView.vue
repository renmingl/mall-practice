<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getSalesRank, getSeckillRank, getSeckillSessions, type SalesRankRow, type SeckillRankRow, type SeckillSession } from '@/api/seckill'

const router = useRouter()

const activeTab = ref(0)
const sessions = ref<SeckillSession[]>([])
const sessionId = ref<number>()
const seckillRank = ref<SeckillRankRow[]>([])
const salesRank = ref<SalesRankRow[]>([])
const loading = ref(false)

/** 排名前三高亮 */
function rankClass(i: number) {
  return i < 3 ? `rank rank-${i + 1}` : 'rank'
}

async function loadSessions() {
  try {
    sessions.value = await getSeckillSessions()
    const ongoing = sessions.value.find((s) => s.phase === 'ongoing')
    sessionId.value = ongoing?.id ?? sessions.value[0]?.id
    if (sessionId.value) loadSeckillRank(sessionId.value)
  } catch {
    showToast('秒杀场次加载失败')
  }
}

async function loadSeckillRank(id: number) {
  loading.value = true
  try {
    seckillRank.value = await getSeckillRank(id, 10)
  } catch {
    seckillRank.value = []
  } finally {
    loading.value = false
  }
}

async function loadSalesRank() {
  loading.value = true
  try {
    salesRank.value = await getSalesRank(10)
  } catch {
    salesRank.value = []
  } finally {
    loading.value = false
  }
}

function onTabChange(index: number | string) {
  if (index === 1 && !salesRank.value.length) {
    loadSalesRank()
  }
}

onMounted(loadSessions)
</script>

<template>
  <div class="rank-page">
    <van-nav-bar title="排行榜" fixed placeholder>
      <template #left>
        <span class="nav-link" @click="router.push('/')">首页</span>
      </template>
    </van-nav-bar>

    <van-tabs v-model:active="activeTab" @change="onTabChange">
      <!-- 秒杀榜：按场次 -->
      <van-tab title="秒杀榜">
        <div class="rank-toolbar">
          <span class="rank-tip">场次：</span>
          <van-dropdown-menu>
            <van-dropdown-item
              v-model="sessionId"
              :options="sessions.map((s) => ({ text: s.name, value: s.id }))"
              @change="sessionId != null && loadSeckillRank(sessionId)"
            />
          </van-dropdown-menu>
        </div>
        <van-loading v-if="loading" class="page-loading" />
        <van-empty v-else-if="!seckillRank.length" description="该场次暂无成交数据" />
        <van-cell v-for="(row, i) in seckillRank" :key="row.skuId" center>
          <template #title>
            <span :class="rankClass(i)">{{ i + 1 }}</span>
            <span class="rank-name">{{ row.spuName || `SKU ${row.skuId}` }}</span>
          </template>
          <template #value>
            <span class="rank-sales">已抢 {{ row.sales }} 件</span>
          </template>
        </van-cell>
      </van-tab>

      <!-- 销量榜：全平台 -->
      <van-tab title="销量榜">
        <van-loading v-if="loading" class="page-loading" />
        <van-empty v-else-if="!salesRank.length" description="暂无销量数据" />
        <van-cell v-for="(row, i) in salesRank" :key="row.skuId" center @click="row.spuId && router.push(`/product/${row.spuId}`)">
          <template #title>
            <span :class="rankClass(i)">{{ i + 1 }}</span>
            <span class="rank-name">{{ row.spuName || `SKU ${row.skuId}` }}</span>
          </template>
          <template #value>
            <span class="rank-sales">已售 {{ row.sales }} 件</span>
          </template>
        </van-cell>
      </van-tab>
    </van-tabs>
  </div>
</template>

<style scoped>
.rank-page {
  max-width: 640px;
  margin: 0 auto;
}
.nav-link {
  color: #1989fa;
  cursor: pointer;
}
.rank-toolbar {
  padding: 8px 16px 0;
  display: flex;
  align-items: center;
}
.rank-tip {
  color: #969799;
  font-size: 13px;
}
.page-loading {
  padding: 40px 0;
}
.rank {
  display: inline-block;
  width: 20px;
  height: 20px;
  margin-right: 8px;
  line-height: 20px;
  text-align: center;
  border-radius: 4px;
  color: #fff;
  background: #c8c9cc;
  font-size: 12px;
}
.rank-1 {
  background: #ee0a24;
}
.rank-2 {
  background: #ff976a;
}
.rank-3 {
  background: #ffc300;
}
.rank-name {
  font-size: 14px;
}
.rank-sales {
  color: #969799;
  font-size: 12px;
}
</style>
