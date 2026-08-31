<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { getMenuTree, type AdminMenu } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const menus = ref<AdminMenu[]>([])
const collapsed = ref(false)

// 菜单 icon 字段（SQL 种子为小写英文名）→ Element Plus 图标组件名映射，未命中回退 Menu
const ICON_MAP: Record<string, string> = {
  setting: 'Setting',
  user: 'User',
  role: 'Avatar',
  menu: 'Menu',
  product: 'Goods',
  order: 'List',
  coupon: 'Ticket',
  seckill: 'Timer',
  member: 'UserFilled',
  trade: 'ShoppingCart'
}

function iconOf(name?: string) {
  return name ? ICON_MAP[name] || name : ''
}

// 侧边栏高亮：当前路由 path
const activeMenu = computed(() => route.path)

/** 拉取权限树（type=1 目录 / 2 菜单参与渲染，type=3 按钮不显示） */
async function loadMenus() {
  try {
    menus.value = (await getMenuTree()).filter((m) => m.status !== 0)
  } catch {
    menus.value = []
  }
}

/** 菜单点击跳转（path 为空或非 / 开头的目录节点不跳） */
function onMenuClick(menu: AdminMenu) {
  if (menu.path?.startsWith('/')) {
    router.push(menu.path)
  }
}

/** 退出登录 */
async function onLogout() {
  await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  await userStore.logout()
  router.replace('/login')
}

onMounted(loadMenus)
</script>

<template>
  <el-container class="layout">
    <!-- 侧边栏 -->
    <el-aside :width="collapsed ? '64px' : '220px'">
      <div class="logo" @click="router.push('/')">mall-admin</div>
      <el-menu :default-active="activeMenu" :collapse="collapsed" background-color="#001529" text-color="#a6adb4" active-text-color="#fff" router>
        <template v-for="menu in menus" :key="menu.id">
          <!-- 有子菜单的目录/菜单：递归渲染 -->
          <el-sub-menu v-if="menu.children && menu.children.length" :index="String(menu.id)">
            <template #title>
              <el-icon v-if="menu.icon"><component :is="iconOf(menu.icon)" /></el-icon>
              <span>{{ menu.name }}</span>
            </template>
            <el-menu-item
              v-for="child in menu.children.filter((c) => c.type !== 3 && c.status !== 0)"
              :key="child.id"
              :index="child.path || String(child.id)"
              @click="onMenuClick(child)"
            >
              {{ child.name }}
            </el-menu-item>
          </el-sub-menu>
          <!-- 无子菜单的菜单节点 -->
          <el-menu-item v-else-if="menu.type !== 3" :index="menu.path || String(menu.id)" @click="onMenuClick(menu)">
            <el-icon v-if="menu.icon"><component :is="iconOf(menu.icon)" /></el-icon>
            <span>{{ menu.name }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 头部 -->
      <el-header class="header">
        <el-icon class="collapse-btn" @click="collapsed = !collapsed">
          <Expand v-if="collapsed" />
          <Fold v-else />
        </el-icon>
        <div class="header-right">
          <el-dropdown @command="(cmd: string) => cmd === 'logout' && onLogout()">
            <span class="user-info">
              <el-avatar :size="28" :src="userStore.user?.avatar || ''">{{ userStore.nickname.charAt(0) }}</el-avatar>
              <span class="nickname">{{ userStore.nickname }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}
.logo {
  height: 56px;
  line-height: 56px;
  text-align: center;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  cursor: pointer;
  background: #002140;
}
.el-aside {
  background: #001529;
  transition: width 0.2s;
}
.el-menu {
  border-right: none;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
}
.collapse-btn {
  font-size: 20px;
  cursor: pointer;
}
.header-right {
  display: flex;
  align-items: center;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}
.nickname {
  font-size: 14px;
  color: #333;
}
.main {
  background: #f0f2f5;
  padding: 16px;
  overflow-y: auto;
}
</style>
