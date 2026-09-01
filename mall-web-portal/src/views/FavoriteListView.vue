<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getFavoriteList, removeFavorite } from '@/api/product'

interface FavoriteRow {
  favoriteId: number
  spuId: number
  name?: string
  subtitle?: string
  mainPic?: string
  price?: number
  createTime: string
}

const router = useRouter()
const loading = ref(false)
const list = ref<FavoriteRow[]>([])
const finished = ref(false)
const page = ref(1)
const size = 10

async function load() {
  loading.value = true
  try {
    const data = await getFavoriteList(page.value, size)
    list.value = [...list.value, ...data.records]
    finished.value = list.value.length >= data.total
    page.value++
  } finally {
    loading.value = false
  }
}

function goDetail(row: FavoriteRow) {
  router.push(`/product/${row.spuId}`)
}

async function onRemove(row: FavoriteRow) {
  await removeFavorite(row.spuId)
  list.value = list.value.filter((f) => f.favoriteId !== row.favoriteId)
}

onMounted(load)
</script>

<template>
  <div class="favorite-list">
    <h2 class="page-title">我的收藏</h2>

    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="load">
      <van-swipe-cell v-for="row in list" :key="row.favoriteId">
        <van-card :title="row.name" :desc="row.subtitle" :thumb="row.mainPic" :price="row.price ? '¥' + row.price.toFixed(2) : ''" @click="goDetail(row)" />
        <template #right>
          <van-button square type="danger" text="删除" class="delete-btn" @click="onRemove(row)" />
        </template>
      </van-swipe-cell>
      <van-empty v-if="!loading && finished && !list.length" description="还没有收藏，去逛逛吧">
        <van-button round type="primary" size="small" @click="router.push('/product/list')">去逛逛</van-button>
      </van-empty>
    </van-list>
  </div>
</template>

<style scoped>
.favorite-list {
  width: min(92vw, 1680px);
  margin: 0 auto;
}
.delete-btn {
  height: 100%;
}
</style>
