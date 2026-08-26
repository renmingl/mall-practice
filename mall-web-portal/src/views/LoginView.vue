<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getCaptcha } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const form = reactive({ username: '', password: '', captchaCode: '' })
const captcha = reactive({ uuid: '', imgBase64: '' })

// 获取图形验证码（uuid + base64 图片；点击图片可刷新）
async function loadCaptcha() {
  const data = await getCaptcha()
  captcha.uuid = data.uuid
  captcha.imgBase64 = data.imgBase64
  form.captchaCode = ''
}

async function onSubmit() {
  if (!form.username || !form.password || !form.captchaCode) {
    showToast('请填写完整信息')
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
    showToast('登录成功')
    // 回跳原页面（无则首页）
    router.replace((route.query.redirect as string) || '/')
  } catch {
    loadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(loadCaptcha)
</script>

<template>
  <div class="login-page">
    <h1 class="brand">mall-practice 商城</h1>
    <van-form @submit="onSubmit">
      <van-cell-group inset>
        <van-field
          v-model="form.username"
          name="username"
          label="用户名"
          placeholder="请输入用户名"
          :rules="[{ required: true, message: '请输入用户名' }]"
        />
        <van-field
          v-model="form.password"
          type="password"
          name="password"
          label="密码"
          placeholder="请输入密码"
          :rules="[{ required: true, message: '请输入密码' }]"
        />
        <van-field
          v-model="form.captchaCode"
          name="captchaCode"
          label="验证码"
          placeholder="请输入验证码"
          maxlength="4"
          :rules="[{ required: true, message: '请输入验证码' }]"
        >
          <template #button>
            <img
              v-if="captcha.imgBase64"
              class="captcha-img"
              :src="captcha.imgBase64"
              alt="验证码"
              title="点击刷新"
              @click="loadCaptcha"
            />
          </template>
        </van-field>
      </van-cell-group>
      <div class="submit-wrap">
        <van-button round block type="primary" native-type="submit" :loading="loading">登 录</van-button>
        <div class="links">
          <router-link to="/register">注册账号</router-link>
          <router-link to="/forgot-password">忘记密码</router-link>
        </div>
      </div>
    </van-form>
  </div>
</template>

<style scoped>
.login-page {
  max-width: 480px;
  margin: 0 auto;
  padding-top: 12vh;
}
.brand {
  text-align: center;
  margin-bottom: 32px;
  color: #1989fa;
}
.captcha-img {
  height: 30px;
  border-radius: 4px;
  cursor: pointer;
  vertical-align: middle;
}
.submit-wrap {
  padding: 24px 16px 0;
}
.links {
  margin-top: 16px;
  text-align: center;
  font-size: 14px;
}
.links a {
  color: #1989fa;
}
.links a + a {
  margin-left: 24px;
}
</style>
