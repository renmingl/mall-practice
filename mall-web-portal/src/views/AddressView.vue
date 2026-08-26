<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import {
  addAddress,
  deleteAddress,
  getAddressList,
  setDefaultAddress,
  updateAddress,
  type MemberAddress
} from '@/api/member'

const list = ref<MemberAddress[]>([])
const loading = ref(false)
// 当前选中地址 id（默认地址自动选中；点击列表项可切换默认）
const chosenId = ref<number | null>(null)

// van-address-list 数据适配（address 字段拼接完整地址）
const addressList = computed(() =>
  list.value.map((a) => ({
    id: a.id!,
    name: a.receiverName,
    tel: a.receiverPhone,
    address: `${a.province || ''}${a.city || ''}${a.district || ''}${a.detailAddress}`
  }))
)

// 新增/编辑弹层
const showEditor = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<MemberAddress>({
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  defaultFlag: 0
})

async function load() {
  loading.value = true
  try {
    list.value = await getAddressList()
    const def = list.value.find((a) => a.defaultFlag === 1)
    chosenId.value = def?.id ?? null
  } finally {
    loading.value = false
  }
}

function openAdd() {
  editingId.value = null
  Object.assign(form, {
    receiverName: '',
    receiverPhone: '',
    province: '',
    city: '',
    district: '',
    detailAddress: '',
    defaultFlag: 0
  })
  showEditor.value = true
}

function openEdit(address: MemberAddress) {
  editingId.value = address.id ?? null
  Object.assign(form, {
    receiverName: address.receiverName,
    receiverPhone: address.receiverPhone,
    province: address.province || '',
    city: address.city || '',
    district: address.district || '',
    detailAddress: address.detailAddress,
    defaultFlag: address.defaultFlag || 0
  })
  showEditor.value = true
}

async function save() {
  if (editingId.value) {
    await updateAddress(editingId.value, { ...form })
  } else {
    await addAddress({ ...form })
  }
  showEditor.value = false
  showToast('保存成功')
  load()
}

async function onDelete() {
  if (!editingId.value) return
  await showConfirmDialog({ title: '删除地址', message: '确定删除该收货地址吗？' })
  await deleteAddress(editingId.value)
  showEditor.value = false
  showToast('已删除')
  load()
}

/** 点击列表项设为默认地址 */
async function onSetDefault(address: MemberAddress) {
  if (address.defaultFlag === 1) return
  await setDefaultAddress(address.id!)
  showToast('已设为默认')
  load()
}

onMounted(load)
</script>

<template>
  <div class="address-page">
    <van-nav-bar title="收货地址" left-arrow @click-left="$router.back()" />

    <van-address-list
      v-model:chosen-address-id="chosenId"
      :list="addressList"
      default-tag-text="默认"
      @add="openAdd"
      @edit="openEdit"
      @click-item="onSetDefault"
    />
    <van-empty v-if="!loading && list.length === 0" description="暂无收货地址，点击下方按钮添加" />

    <!-- 新增/编辑弹层 -->
    <van-popup v-model:show="showEditor" position="bottom" round>
      <van-form @submit="save">
        <van-cell-group inset>
          <van-field
            v-model="form.receiverName"
            name="receiverName"
            label="收货人"
            placeholder="请输入收货人姓名"
            :rules="[{ required: true, message: '请输入收货人姓名' }]"
          />
          <van-field
            v-model="form.receiverPhone"
            type="tel"
            name="receiverPhone"
            label="电话"
            placeholder="请输入收货人电话"
            :rules="[{ required: true, message: '请输入收货人电话' }]"
          />
          <van-field v-model="form.province" name="province" label="省" placeholder="选填" />
          <van-field v-model="form.city" name="city" label="市" placeholder="选填" />
          <van-field v-model="form.district" name="district" label="区/县" placeholder="选填" />
          <van-field
            v-model="form.detailAddress"
            name="detailAddress"
            label="详细地址"
            placeholder="街道、门牌号等"
            :rules="[{ required: true, message: '请输入详细地址' }]"
          />
          <van-cell title="设为默认地址">
            <template #right-icon>
              <van-switch v-model="form.defaultFlag" :checked-value="1" :unchecked-value="0" size="20" />
            </template>
          </van-cell>
        </van-cell-group>
        <div style="margin: 16px">
          <van-button round block type="primary" native-type="submit">保存</van-button>
          <van-button v-if="editingId" round block plain type="danger" style="margin-top: 12px" @click="onDelete">
            删除该地址
          </van-button>
        </div>
      </van-form>
    </van-popup>
  </div>
</template>

<style scoped>
.address-page {
  min-height: 100vh;
  background: #f7f8fa;
}
</style>
