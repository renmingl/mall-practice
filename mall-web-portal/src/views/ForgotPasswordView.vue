<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { forgotPassword, getSmsCode } from '@/api/auth'

const router = useRouter()

const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)
const form = reactive({ phone: '', smsCode: '', newPassword: '', confirmPassword: '' })

// 发送短信验证码（后端 60 秒频控；开发期为模拟发送，接口直接返回验证码）
async function sendSms() {
  if (!/^1\d{10}$/.test(form.phone)) {
    showToast('请输入正确的手机号')
    return
  }
  sending.value = true
  try {
    const data = await getSmsCode(form.phone)
    // 开发期模拟短信：后端直接返回验证码，此处提示便于联调；接入真实短信网关后删除
    showToast(`验证码已发送${import.meta.env.DEV ? `：${data.smsCode}` : ''}`)
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch {
    // 频控/错误提示已由拦截器统一处理
  } finally {
    sending.value = false
  }
}

async function onSubmit() {
  if (!form.phone || !form.smsCode || !form.newPassword) {
    showToast('请填写完整信息')
    return
  }
  if (form.newPassword.length < 6) {
    showToast('新密码至少 6 位')
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    showToast('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    await forgotPassword({
      phone: form.phone,
      smsCode: form.smsCode,
      newPassword: form.newPassword
    })
    showToast('密码已重置，请重新登录')
    router.replace('/login')
  } catch {
    // 错误提示已由拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="forgot-page">
    <h1 class="brand">找回密码</h1>
    <van-form @submit="onSubmit">
      <van-cell-group inset>
        <van-field
          v-model="form.phone"
          type="tel"
          name="phone"
          label="手机号"
          placeholder="请输入注册手机号"
          maxlength="11"
          :rules="[{ required: true, message: '请输入手机号' }]"
        />
        <van-field
          v-model="form.smsCode"
          name="smsCode"
          label="短信验证码"
          placeholder="请输入短信验证码"
          maxlength="6"
          :rules="[{ required: true, message: '请输入短信验证码' }]"
        >
          <template #button>
            <van-button
              size="small"
              type="primary"
              plain
              :disabled="countdown > 0"
              :loading="sending"
              @click.prevent="sendSms"
            >
              {{ countdown > 0 ? `${countdown}s 后重发` : '发送验证码' }}
            </van-button>
          </template>
        </van-field>
        <van-field
          v-model="form.newPassword"
          type="password"
          name="newPassword"
          label="新密码"
          placeholder="至少 6 位"
          :rules="[{ required: true, message: '请输入新密码' }]"
        />
        <van-field
          v-model="form.confirmPassword"
          type="password"
          name="confirmPassword"
          label="确认密码"
          placeholder="再次输入新密码"
          :rules="[{ required: true, message: '请再次输入新密码' }]"
        />
      </van-cell-group>
      <div class="submit-wrap">
        <van-button round block type="primary" native-type="submit" :loading="loading">重置密码</van-button>
        <div class="links">
          <router-link to="/login">返回登录</router-link>
        </div>
      </div>
    </van-form>
  </div>
</template>

<style scoped>
.forgot-page {
  max-width: 560px;
  margin: 0 auto;
  padding-top: 10vh;
}
.brand {
  text-align: center;
  margin-bottom: 28px;
  color: #1989fa;
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
