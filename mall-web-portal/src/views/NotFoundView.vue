<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <h1>404</h1>
    <p>页面不存在</p>
    <van-button type="primary" @click="router.push('/')">返回首页</van-button>
  </div>
</template>

<style scoped>
.not-found {
  text-align: center;
  padding-top: 120px;
}
</style>
