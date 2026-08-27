<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getBrands, getCategories, getProductPage, type Brand, type CategoryNode, type Spu } from '@/api/product'

const router = useRouter()

const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const list = ref<Spu[]>([])
const page = ref(1)
const size = 10

// 筛选条件
const categoryOptions = ref<CategoryNode[]>([])
const brandOptions = ref<Brand[]>([])
const activeCategory = ref<number>()
const activeBrand = ref<number>()
const keyword = ref('')

async function loadOptions() {
  categoryOptions.value = await getCategories()
  brandOptions.value = await getBrands()
}

/** 分类树拍平为下拉选项（商品通常挂在三级分类，需可选任意层级） */
function flattenCategories(nodes: CategoryNode[]): { text: string; value: number }[] {
  const result: { text: string; value: number }[] = []
  const walk = (list: CategoryNode[], depth: number) => {
    for (const c of list) {
      result.push({ text: '　'.repeat(depth) + c.name, value: c.id })
      if (c.children?.length) walk(c.children, depth + 1)
    }
  }
  walk(nodes, 0)
  return result
}

async function loadList() {
  loading.value = true
  try {
    const data = await getProductPage(page.value, size, {
      categoryId: activeCategory.value,
      brandId: activeBrand.value,
      keyword: keyword.value || undefined
    })
    list.value = refreshing.value ? data.records : [...list.value, ...data.records]
    finished.value = list.value.length >= data.total
    page.value++
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

/** 筛选变化：重置并刷新 */
function onFilterChange() {
  page.value = 1
  list.value = []
  finished.value = false
  loadList()
}

function onRefresh() {
  refreshing.value = true
  page.value = 1
  finished.value = false
  loadList()
}

function goDetail(spu: Spu) {
  router.push(`/product/${spu.id}`)
}

onMounted(async () => {
  try {
    await loadOptions()
  } catch {
    showToast('分类/品牌加载失败，请确认 mall-product 已启动')
  }
  loadList()
})
</script>

<template>
  <div class="product-list">
    <van-nav-bar title="商品列表" fixed placeholder>
      <template #left>
        <span class="nav-link" @click="router.push('/')">首页</span>
      </template>
      <template #right>
        <span class="nav-link" @click="router.push('/favorites')">收藏</span>
      </template>
    </van-nav-bar>

    <!-- 筛选栏 -->
    <van-search v-model="keyword" placeholder="搜索商品名称" @search="onFilterChange" @clear="onFilterChange" />

    <div class="filter-bar">
      <van-dropdown-menu>
        <van-dropdown-item v-model="activeCategory" title="分类" :options="flattenCategories(categoryOptions)" @change="onFilterChange" />
        <van-dropdown-item v-model="activeBrand" title="品牌" :options="brandOptions.map((b) => ({ text: b.name, value: b.id }))" @change="onFilterChange" />
      </van-dropdown-menu>
    </div>

    <!-- 商品列表（触底加载） -->
    <van-list v-model:loading="loading" v-model:refreshing="refreshing" :finished="finished" finished-text="没有更多了" @load="loadList" @refresh="onRefresh">
      <van-card
        v-for="spu in list"
        :key="spu.id"
        :title="spu.name"
        :desc="spu.subtitle"
        :thumb="spu.mainPic"
        :num="spu.sales ?? 0"
        :tag="spu.unit"
        class="product-card"
        @click="goDetail(spu)"
      >
        <template #footer>
          <span class="sales">已售 {{ spu.sales ?? 0 }} 件 · 点击查看价格</span>
        </template>
      </van-card>
      <van-empty v-if="!loading && finished && !list.length" description="暂无商品" />
    </van-list>
  </div>
</template>

<style scoped>
.product-list {
  max-width: 640px;
  margin: 0 auto;
}
.nav-link {
  color: #1989fa;
  cursor: pointer;
}
.filter-bar {
  margin-bottom: 4px;
}
.product-card {
  margin-bottom: 8px;
}
.sales {
  color: #969799;
  font-size: 12px;
}
</style>
