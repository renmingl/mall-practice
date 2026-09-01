<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getCaptcha } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  phone: '',
  email: '',
  captchaCode: ''
})
const captcha = reactive({ uuid: '', imgBase64: '' })

// 获取图形验证码（点击图片可刷新）
async function loadCaptcha() {
  const data = await getCaptcha()
  captcha.uuid = data.uuid
  captcha.imgBase64 = data.imgBase64
  form.captchaCode = ''
}

async function onSubmit() {
  if (form.password !== form.confirmPassword) {
    showToast('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    await userStore.loginOrRegister({
      username: form.username,
      password: form.password,
      nickname: form.nickname || undefined,
      phone: form.phone || undefined,
      email: form.email || undefined,
      captchaUuid: captcha.uuid,
      captchaCode: form.captchaCode
    })
    showToast('注册成功，已自动登录')
    router.replace('/')
  } catch {
    loadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(loadCaptcha)
</script>

<template>
  <div class="register-page">
    <h1 class="brand">注册账号</h1>
    <van-form @submit="onSubmit">
      <van-cell-group inset>
        <van-field
          v-model="form.username"
          name="username"
          label="用户名"
          placeholder="登录账号（唯一）"
          :rules="[{ required: true, message: '请输入用户名' }]"
        />
        <van-field
          v-model="form.password"
          type="password"
          name="password"
          label="密码"
          placeholder="至少 6 位"
          :rules="[{ required: true, message: '请输入密码' }]"
        />
        <van-field
          v-model="form.confirmPassword"
          type="password"
          name="confirmPassword"
          label="确认密码"
          placeholder="再次输入密码"
          :rules="[{ required: true, message: '请再次输入密码' }]"
        />
        <van-field v-model="form.nickname" name="nickname" label="昵称" placeholder="选填" />
        <van-field v-model="form.phone" type="tel" name="phone" label="手机号" placeholder="选填（找回密码用）" />
        <van-field v-model="form.email" type="email" name="email" label="邮箱" placeholder="选填" />
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
        <van-button round block type="primary" native-type="submit" :loading="loading">注 册</van-button>
        <div class="links">
          已有账号？<router-link to="/login">去登录</router-link>
        </div>
      </div>
    </van-form>
  </div>
</template>

<style scoped>
.register-page {
  max-width: 560px;
  margin: 0 auto;
  padding-top: 8vh;
}
.brand {
  text-align: center;
  margin-bottom: 24px;
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
</style>
