<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAdminCaptcha } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const form = reactive({ username: '', password: '', captchaCode: '' })
const captcha = reactive({ uuid: '', imgBase64: '' })

// 获取图形验证码（uuid + base64 图片；点击图片可刷新）
async function loadCaptcha() {
  const data = await getAdminCaptcha()
  captcha.uuid = data.uuid
  captcha.imgBase64 = data.imgBase64
  form.captchaCode = ''
}

async function onSubmit() {
  if (!form.username || !form.password || !form.captchaCode) {
    ElMessage.warning('请输入用户名、密码和验证码')
    return
  }
  loading.value = true
  try {
    await userStore.login({
      username: form.username,
      password: form.password,
      captchaUuid: captcha.uuid,
      captchaCode: form.captchaCode
    })
    ElMessage.success('登录成功')
    router.replace((route.query.redirect as string) || '/')
  } catch {
    // 错误提示已由拦截器统一处理；验证码一次一用，失败后刷新
    loadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(loadCaptcha)
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <h1 class="brand">mall-practice 管理后台</h1>
      <p class="tip">默认账号：admin / admin123（超级管理员）</p>
      <el-form :model="form" size="large" @submit.prevent="onSubmit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" clearable autocomplete="username">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            show-password
            autocomplete="current-password"
            @keyup.enter="onSubmit"
          >
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.captchaCode"
            placeholder="验证码"
            maxlength="4"
            clearable
            autocomplete="off"
            @keyup.enter="onSubmit"
          >
            <template #prefix><el-icon><Key /></el-icon></template>
          </el-input>
          <img
            v-if="captcha.imgBase64"
            class="captcha-img"
            :src="captcha.imgBase64"
            alt="验证码"
            title="点击刷新"
            @click="loadCaptcha"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="onSubmit">
          登 录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f3b73 0%, #2d5aa8 100%);
}
.login-card {
  width: 380px;
  padding: 12px 8px;
}
.brand {
  text-align: center;
  font-size: 22px;
  margin-bottom: 4px;
  color: #2d5aa8;
}
.tip {
  text-align: center;
  color: #909399;
  font-size: 12px;
  margin-bottom: 20px;
}
.submit-btn {
  width: 100%;
}
.captcha-img {
  height: 40px;
  margin-left: 8px;
  border-radius: 4px;
  cursor: pointer;
  flex-shrink: 0;
}
</style>
