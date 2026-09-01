<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { getPoints, getProfile, updateProfile, type MemberProfile } from '@/api/member'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const profile = ref<MemberProfile | null>(null)
const pointsData = ref<
  { points: number; level: number; levelInfo: { name: string; discount: number } } | null
>(null)

// 编辑表单（弹层）
const showEdit = ref(false)
const editForm = reactive({ nickname: '', email: '', gender: 0, birthday: '' })

async function load() {
  loading.value = true
  try {
    const [p, pt] = await Promise.all([getProfile(), getPoints()])
    profile.value = p
    pointsData.value = pt
    // 同步 store 中的昵称/头像（顶部展示用）
    if (userStore.user) {
      userStore.user.nickname = p.nickname
    }
  } finally {
    loading.value = false
  }
}

function openEdit() {
  if (!profile.value) return
  editForm.nickname = profile.value.nickname || ''
  editForm.email = profile.value.email || ''
  editForm.gender = profile.value.gender ?? 0
  editForm.birthday = profile.value.birthday || ''
  showEdit.value = true
}

async function saveEdit() {
  await updateProfile({
    nickname: editForm.nickname,
    email: editForm.email,
    gender: editForm.gender,
    birthday: editForm.birthday || undefined
  })
  showEdit.value = false
  showToast('保存成功')
  load()
}

async function onLogout() {
  await showConfirmDialog({ title: '退出登录', message: '确定退出当前账号吗？' })
  await userStore.logout()
  router.replace('/login')
}

onMounted(load)
</script>

<template>
  <div class="profile-page">
    <!-- 用户信息头 -->
    <div class="header">
      <van-image round width="64" height="64" :src="profile?.avatar || ''" fit="cover">
        <template #error>
          <div class="avatar-fallback">{{ (profile?.nickname || 'U').charAt(0) }}</div>
        </template>
      </van-image>
      <div class="info">
        <p class="nickname">{{ profile?.nickname || userStore.nickname }}</p>
        <p class="username">@{{ profile?.username }}</p>
      </div>
      <van-button size="small" round plain type="primary" @click="openEdit">编辑资料</van-button>
    </div>

    <!-- 等级与积分 -->
    <van-cell-group inset title="等级权益">
      <van-cell title="会员等级" :value="pointsData?.levelInfo.name || '-'" />
      <van-cell title="当前积分" :value="`${pointsData?.points ?? 0} 分`" />
      <van-cell
        title="会员折扣"
        :value="pointsData ? `${(pointsData.levelInfo.discount * 10).toFixed(1)} 折` : '-'"
      />
    </van-cell-group>

    <!-- 我的服务 -->
    <van-cell-group inset title="我的服务">
      <van-cell title="收货地址" is-link to="/address" icon="location-o" />
      <van-cell title="退出登录" is-link @click="onLogout" />
    </van-cell-group>

    <!-- 阶段 7：秒杀与运营入口 -->
    <van-cell-group inset title="限时秒杀">
      <van-cell title="秒杀会场" is-link to="/seckill" icon="flash" />
      <van-cell title="排行榜" is-link to="/rank" icon="award-o" />
    </van-cell-group>

    <van-cell-group inset title="我的数据">
      <van-cell title="每日签到" is-link to="/checkin" icon="calendar-o" />
      <van-cell title="浏览足迹" is-link to="/history" icon="eye-o" />
    </van-cell-group>

    <!-- 编辑弹层 -->
    <van-popup v-model:show="showEdit" position="bottom" round>
      <van-form @submit="saveEdit">
        <van-cell-group inset>
          <van-field v-model="editForm.nickname" name="nickname" label="昵称" placeholder="请输入昵称" />
          <van-field v-model="editForm.email" name="email" label="邮箱" placeholder="请输入邮箱" />
          <van-field name="gender" label="性别">
            <template #input>
              <van-radio-group v-model="editForm.gender" direction="horizontal">
                <van-radio :name="0">未知</van-radio>
                <van-radio :name="1">男</van-radio>
                <van-radio :name="2">女</van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <van-field v-model="editForm.birthday" name="birthday" label="生日" placeholder="YYYY-MM-DD" />
        </van-cell-group>
        <div style="margin: 16px">
          <van-button round block type="primary" native-type="submit">保存</van-button>
        </div>
      </van-form>
    </van-popup>
  </div>
</template>

<style scoped>
.profile-page {
  max-width: 640px;
  margin: 0 auto;
  padding: 16px;
}
.header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px 16px;
  background: #fff;
  border-radius: 8px;
}
.avatar-fallback {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: #fff;
  background: #1989fa;
}
.info {
  flex: 1;
}
.nickname {
  font-size: 18px;
  font-weight: 600;
}
.username {
  color: #969799;
  font-size: 13px;
  margin-top: 4px;
}
</style>
