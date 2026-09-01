<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getSearch, getSearchSuggest, type SearchRecord } from '@/api/search'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const list = ref<SearchRecord[]>([])
const total = ref(0)
const loading = ref(false)
const finished = ref(false)
const page = ref(1)
const size = 10
const fallback = ref(false)

// 联想：输入防抖 300ms 请求前缀候选；blur 延迟关闭避免吞掉联想项点击
const suggestions = ref<string[]>([])
const showSuggest = ref(false)
let suggestTimer: number | undefined

async function loadList() {
  loading.value = true
  try {
    const data = await getSearch(keyword.value || undefined, undefined, page.value, size)
    fallback.value = !!data.fallback
    total.value = data.total
    list.value = [...list.value, ...data.records]
    finished.value = list.value.length >= data.total
    page.value++
  } catch {
    showToast('搜索失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

/** 重新搜索（回车/点击联想/清空） */
function doSearch() {
  showSuggest.value = false
  page.value = 1
  list.value = []
  finished.value = false
  total.value = 0
  fallback.value = false
  loadList()
}

/** 输入防抖联想 */
function onKeywordChange() {
  if (suggestTimer) window.clearTimeout(suggestTimer)
  const kw = keyword.value.trim()
  if (!kw) {
    suggestions.value = []
    showSuggest.value = false
    return
  }
  suggestTimer = window.setTimeout(async () => {
    try {
      suggestions.value = await getSearchSuggest(kw)
      showSuggest.value = suggestions.value.length > 0
    } catch {
      // 联想失败不阻塞搜索
      suggestions.value = []
      showSuggest.value = false
    }
  }, 300)
}

function onSelectSuggest(name: string) {
  keyword.value = name
  doSearch()
}

function goDetail(r: SearchRecord) {
  router.push(`/product/${r.spuId}`)
}

onMounted(() => {
  const kw = route.query.keyword
  if (typeof kw === 'string' && kw.trim()) {
    keyword.value = kw.trim()
    doSearch()
  }
})
</script>

<template>
  <div class="search-page">
    <van-nav-bar title="商品搜索" fixed placeholder>
      <template #left>
        <span class="nav-link" @click="router.push('/')">首页</span>
      </template>
    </van-nav-bar>

    <div class="search-wrap">
      <van-search
        v-model="keyword"
        placeholder="搜索商品（ES 全文检索）"
        show-action
        @update:model-value="onKeywordChange"
        @search="doSearch"
        @clear="doSearch"
        @blur="setTimeout(() => (showSuggest = false), 200)"
      >
        <template #action>
          <span @click="doSearch">搜索</span>
        </template>
      </van-search>

      <!-- 联想下拉 -->
      <div v-if="showSuggest && suggestions.length" class="suggest-box">
        <van-cell v-for="s in suggestions" :key="s" :title="s" icon="search" @mousedown.prevent @click="onSelectSuggest(s)" />
      </div>
    </div>

    <!-- ES 不可用降级提示 -->
    <van-notice-bar v-if="fallback" left-icon="warning-o" text="搜索服务暂不可用（Elasticsearch 未启动），请检查 mall-search 与 ES 后重试" />

    <p v-if="!loading && total > 0" class="result-tip">共 {{ total }} 条结果</p>

    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadList">
      <van-card
        v-for="r in list"
        :key="r.spuId"
        class="search-card"
        :thumb="r.pic"
        @click="goDetail(r)"
      >
        <template #title>
          <!-- ES 高亮片段含 <em> 标记，仅渲染后端返回的受控内容 -->
          <span v-html="r.highlightName || r.name"></span>
        </template>
        <template #desc>
          <span v-if="r.highlightSubtitle" v-html="r.highlightSubtitle"></span>
          <span v-else>{{ r.subtitle }}</span>
        </template>
        <template #tags>
          <van-tag v-if="r.categoryName" plain type="primary">{{ r.categoryName }}</van-tag>
          <van-tag v-if="r.brandName" plain>{{ r.brandName }}</van-tag>
        </template>
        <template #footer>
          <div class="card-footer">
            <span class="price">¥{{ r.price?.toFixed(2) ?? '-' }}</span>
            <span class="sales">已售 {{ r.sales ?? 0 }} 件</span>
          </div>
        </template>
      </van-card>
      <van-empty v-if="!loading && finished && !list.length" :description="fallback ? '搜索服务暂不可用' : '没有找到相关商品'" />
    </van-list>
  </div>
</template>

<style scoped>
.search-page {
  max-width: 640px;
  margin: 0 auto;
  padding-bottom: 24px;
}
.nav-link {
  color: #1989fa;
  cursor: pointer;
}
.search-wrap {
  position: relative;
}
.suggest-box {
  position: absolute;
  top: 54px;
  left: 8px;
  right: 8px;
  z-index: 10;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  overflow: hidden;
}
.result-tip {
  padding: 0 16px;
  color: #969799;
  font-size: 12px;
}
.search-card {
  margin-bottom: 8px;
}
.search-card :deep(.van-card__title) em {
  color: #ee0a24;
  font-style: normal;
}
.search-card :deep(.van-card__desc) em {
  color: #ee0a24;
  font-style: normal;
}
.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.price {
  color: #ee0a24;
  font-weight: 600;
  font-size: 16px;
}
.sales {
  color: #969799;
  font-size: 12px;
}
</style>
