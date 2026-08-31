<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { createComment } from '@/api/comment'

const route = useRoute()
const router = useRouter()
const orderItemId = Number(route.query.orderItemId || 0)
const orderSn = String(route.query.orderSn || '')

const rating = ref(5)
const content = ref('')
const submitting = ref(false)

async function onSubmit() {
  if (!orderItemId) {
    showToast('缺少订单项信息')
    router.replace('/orders')
    return
  }
  submitting.value = true
  try {
    await createComment({ orderItemId, rating: rating.value, content: content.value.trim() || undefined })
    showToast('评价成功')
    router.replace(orderSn ? `/order/${orderSn}` : '/orders')
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="comment-page">
    <van-nav-bar title="发表评价" fixed placeholder>
      <template #left>
        <van-icon name="arrow-left" @click="router.back()" />
      </template>
    </van-nav-bar>

    <van-cell-group inset title="商品评分">
      <div class="rate-wrap">
        <van-rate v-model="rating" :size="28" color="#ee0a24" void-icon="star" void-color="#eee" />
      </div>
    </van-cell-group>

    <van-cell-group inset title="评价内容">
      <van-field
        v-model="content"
        rows="4"
        type="textarea"
        maxlength="500"
        show-word-limit
        placeholder="说说这件商品的使用感受吧（选填）"
      />
    </van-cell-group>

    <div class="submit-bar">
      <van-button type="danger" round block :loading="submitting" @click="onSubmit">提交评价</van-button>
    </div>
  </div>
</template>

<style scoped>
.comment-page {
  max-width: 640px;
  margin: 0 auto;
  padding-bottom: 80px;
}
.rate-wrap {
  padding: 16px;
}
.submit-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  max-width: 640px;
  margin: 0 auto;
  padding: 12px 16px;
  background: #fff;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
}
</style>
