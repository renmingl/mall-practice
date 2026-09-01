<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getBrands, getCategories, getProductPage, type Brand, type CategoryNode, type Spu } from '@/api/product'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const finished = ref(false)
const list = ref<Spu[]>([])
const page = ref(1)
const size = 10

// 筛选条件（支持 query 透传：分类入口/搜索跳转）
const categoryOptions = ref<CategoryNode[]>([])
const brandOptions = ref<Brand[]>([])
const activeCategory = ref<number>()
const activeBrand = ref<number>()
const keyword = ref('')

async function loadOptions() {
  try {
    categoryOptions.value = await getCategories()
    brandOptions.value = await getBrands()
  } catch {
    showToast('分类/品牌加载失败')
  }
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
    list.value = [...list.value, ...data.records]
    finished.value = list.value.length >= data.total
    page.value++
  } finally {
    loading.value = false
  }
}

/** 筛选变化：重置并刷新 */
function onFilterChange() {
  page.value = 1
  list.value = []
  finished.value = false
  loadList()
}

function goDetail(spu: Spu) {
  router.push(`/product/${spu.id}`)
}

onMounted(async () => {
  // 从路由 query 初始化筛选（首页分类入口 / SiteHeader 搜索跳转）
  const qCategory = route.query.categoryId
  const qBrand = route.query.brandId
  const qKeyword = route.query.keyword
  if (typeof qCategory === 'string' && qCategory) activeCategory.value = Number(qCategory)
  if (typeof qBrand === 'string' && qBrand) activeBrand.value = Number(qBrand)
  if (typeof qKeyword === 'string' && qKeyword) keyword.value = qKeyword
  await loadOptions()
  loadList()
})
</script>

<template>
  <div class="product-list">
    <div class="page-head">
      <h2>全部商品</h2>
    </div>

    <div class="toolbar">
      <div class="search-wrap">
        <van-search
          v-model="keyword"
          placeholder="搜索商品名称"
          shape="round"
          @search="onFilterChange"
          @clear="onFilterChange"
        />
      </div>
      <div class="filter-bar">
        <van-dropdown-menu>
          <van-dropdown-item
            v-model="activeCategory"
            title="分类"
            :options="flattenCategories(categoryOptions)"
            @change="onFilterChange"
          />
          <van-dropdown-item
            v-model="activeBrand"
            title="品牌"
            :options="brandOptions.map((b) => ({ text: b.name, value: b.id }))"
            @change="onFilterChange"
          />
        </van-dropdown-menu>
      </div>
    </div>

    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadList">
      <div class="grid">
        <div v-for="spu in list" :key="spu.id" class="p-card" @click="goDetail(spu)">
          <img :src="spu.mainPic" :alt="spu.name" class="p-img" loading="lazy" />
          <div class="p-info">
            <p class="p-name" :title="spu.name">{{ spu.name }}</p>
            <p class="p-sub" :title="spu.subtitle">{{ spu.subtitle }}</p>
            <div class="p-bottom">
              <span class="p-price">¥{{ (spu.price ?? 0).toFixed(2) }}</span>
              <span class="p-sales">已售 {{ spu.sales ?? 0 }} 件</span>
            </div>
          </div>
        </div>
      </div>
      <van-empty v-if="!loading && finished && !list.length" description="暂无商品" />
    </van-list>
  </div>
</template>

<style scoped>
.product-list {
  width: min(92vw, 1680px);
  margin: 0 auto;
  padding: 16px;
}
.page-head h2 {
  margin: 4px 0 14px;
  font-size: 22px;
  color: #323233;
  border-left: 4px solid #ee0a24;
  padding-left: 10px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fff;
  border-radius: 10px;
  padding: 8px 12px;
  margin-bottom: 16px;
}
.search-wrap {
  flex: 1;
  min-width: 220px;
}
.filter-bar {
  flex-shrink: 0;
}
.grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.p-card {
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}
.p-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
  transform: translateY(-3px);
}
.p-img {
  width: 100%;
  aspect-ratio: 1 / 1;
  object-fit: cover;
  background: #f2f3f5;
  display: block;
}
.p-info {
  padding: 12px 14px 14px;
}
.p-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.p-sub {
  margin: 6px 0 0;
  font-size: 12px;
  color: #969799;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.p-bottom {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 10px;
}
.p-price {
  color: #ee0a24;
  font-size: 18px;
  font-weight: 700;
}
.p-sales {
  color: #969799;
  font-size: 12px;
}
</style>
