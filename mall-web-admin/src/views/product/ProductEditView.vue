<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import {
  getBrandPage,
  getCategoryTree,
  getProductDetail,
  saveProduct,
  uploadImage,
  type Brand,
  type CategoryNode,
  type Sku
} from '@/api/product'

const route = useRoute()
const router = useRouter()
const spuId = route.params.id ? Number(route.params.id) : undefined

const loading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  id: undefined as number | undefined,
  spuCode: '',
  categoryId: undefined as number | undefined,
  brandId: undefined as number | undefined,
  name: '',
  subtitle: '',
  mainPic: '',
  pics: '',
  unit: '',
  detail: '',
  status: 1,
  sort: 0
})

const skuList = ref<Sku[]>([])

// ---------- 下拉数据 ----------

const categoryTree = ref<CategoryNode[]>([])
const brandOptions = ref<Brand[]>([])

async function loadOptions() {
  categoryTree.value = await getCategoryTree()
  const data = await getBrandPage(1, 100, undefined, 1)
  brandOptions.value = data.records
}

// ---------- 编辑回显 ----------

async function loadDetail() {
  if (!spuId) return
  loading.value = true
  try {
    const data = await getProductDetail(spuId)
    const spu = data.spu
    Object.assign(form, {
      id: spu.id,
      spuCode: spu.spuCode,
      categoryId: spu.categoryId,
      brandId: spu.brandId,
      name: spu.name,
      subtitle: spu.subtitle || '',
      mainPic: spu.mainPic || '',
      pics: spu.pics || '',
      unit: spu.unit || '',
      detail: spu.detail || '',
      status: spu.status ?? 1,
      sort: spu.sort ?? 0
    })
    skuList.value = data.skuList.map((s) => ({ ...s }))
  } finally {
    loading.value = false
  }
}

// ---------- SKU 行操作 ----------

function addSkuRow() {
  skuList.value.push({ id: undefined, skuCode: '', spec: '', price: undefined, lowStock: 10, status: 1 })
}

function removeSkuRow(index: number) {
  skuList.value.splice(index, 1)
}

// ---------- 图片上传 ----------

const uploading = ref(false)

async function onUploadPic(options: { file: File; onSuccess: (url: string) => void; onError: (err: Error) => void }) {
  uploading.value = true
  try {
    const url = await uploadImage(options.file)
    options.onSuccess(url)
  } catch (err) {
    options.onError(err as Error)
  } finally {
    uploading.value = false
  }
}

// ---------- 保存 ----------

async function submitForm() {
  await formRef.value?.validate()
  if (!form.categoryId) {
    ElMessage.warning('请选择商品分类')
    return
  }
  if (!skuList.value.length) {
    ElMessage.warning('至少需要一个 SKU')
    return
  }
  const invalidSku = skuList.value.find((s) => !s.skuCode || s.price === undefined || s.price <= 0)
  if (invalidSku) {
    ElMessage.warning('SKU 编码和售价（>0）必填')
    return
  }
  await saveProduct({
    id: form.id,
    spuCode: form.spuCode,
    categoryId: form.categoryId,
    brandId: form.brandId,
    name: form.name,
    subtitle: form.subtitle,
    mainPic: form.mainPic,
    pics: form.pics,
    unit: form.unit,
    detail: form.detail,
    status: form.status,
    sort: form.sort,
    skuList: skuList.value.map((s) => ({
      id: s.id,
      skuCode: s.skuCode,
      spec: s.spec,
      price: s.price,
      lowStock: s.lowStock,
      status: s.status
    }))
  })
  ElMessage.success(form.id ? '保存成功' : '新增成功')
  router.push('/product')
}

onMounted(() => {
  loadOptions()
  loadDetail()
})
</script>

<template>
  <div class="page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ spuId ? '编辑商品' : '新增商品' }}</span>
          <el-button link @click="router.push('/product')">返回列表</el-button>
        </div>
      </template>

      <el-form ref="formRef" :model="form" label-width="90px">
        <!-- 基本信息 -->
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="商品编码" prop="spuCode" :rules="[{ required: true, message: '请输入商品编码' }]">
              <el-input v-model="form.spuCode" placeholder="唯一编码，如 SPU10001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品名称" prop="name" :rules="[{ required: true, message: '请输入商品名称' }]">
              <el-input v-model="form.name" placeholder="商品标题" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类">
              <el-tree-select
                v-model="form.categoryId"
                :data="categoryTree"
                :props="{ label: 'name', children: 'children' }"
                check-strictly
                clearable
                placeholder="选择商品分类"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌">
              <el-select v-model="form.brandId" clearable filterable placeholder="选择品牌" style="width: 100%">
                <el-option v-for="b in brandOptions" :key="b.id" :label="b.name" :value="b.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="副标题">
              <el-input v-model="form.subtitle" placeholder="卖点描述（选填）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位">
              <el-input v-model="form.unit" placeholder="如：件、盒" style="width: 120px" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="主图">
              <el-upload :show-file-list="false" :http-request="onUploadPic" accept="image/*">
                <el-image v-if="form.mainPic" :src="form.mainPic" fit="cover" style="width: 80px; height: 80px; border-radius: 4px" />
                <el-button v-else :loading="uploading">上传主图</el-button>
              </el-upload>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sort" :min="0" :max="9999" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">上架</el-radio>
                <el-radio :value="0">下架</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="详情">
              <el-input v-model="form.detail" type="textarea" :rows="4" placeholder="商品详情描述（HTML 或纯文本，选填）" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- SKU 列表 -->
        <el-divider content-position="left">SKU 规格（库存以原值为准，仅经入库/盘点变动）</el-divider>
        <el-table :data="skuList" border size="small">
          <el-table-column label="SKU 编码" min-width="160">
            <template #default="{ row }">
              <el-input v-model="row.skuCode" placeholder="如：SKU10001-黑-64G" />
            </template>
          </el-table-column>
          <el-table-column label="规格" min-width="160">
            <template #default="{ row }">
              <el-input v-model="row.spec" placeholder="如：黑色 64G" />
            </template>
          </el-table-column>
          <el-table-column label="售价" width="140">
            <template #default="{ row }">
              <el-input-number v-model="row.price" :min="0.01" :precision="2" :controls="false" style="width: 110px" placeholder="0.00" />
            </template>
          </el-table-column>
          <el-table-column label="预警阈值" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.lowStock" :min="0" :controls="false" style="width: 90px" />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-switch v-model="row.status" :active-value="1" :inactive-value="0" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeSkuRow($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button class="add-sku-btn" type="primary" plain @click="addSkuRow">+ 添加 SKU</el-button>

        <div class="footer">
          <el-button @click="router.push('/product')">取消</el-button>
          <el-button type="primary" :loading="uploading" @click="submitForm">保存</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.add-sku-btn {
  margin-top: 12px;
}
.footer {
  margin-top: 24px;
  text-align: right;
}
</style>
