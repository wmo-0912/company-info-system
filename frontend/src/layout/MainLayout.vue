<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <img v-if="company.logoUrl" :src="company.logoUrl" alt="公司 Logo" class="logo-img" />
        <span v-else class="logo-text">{{ company.name }}</span>
      </div>
      <el-menu :default-active="activePath" router background-color="#001529" text-color="#a6adb4"
        active-text-color="#ffffff" class="menu">
        <template v-for="item in menus" :key="item.code">
          <el-sub-menu v-if="item.children.length" :index="item.code">
            <template #title>
              <el-icon><component :is="iconOf(item.icon)" /></el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item v-for="child in item.children" :key="child.code" :index="child.path || child.code">
              {{ child.title }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path || item.code">
            <el-icon><component :is="iconOf(item.icon)" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-title">{{ currentTitle }}</div>
        <el-dropdown trigger="click" @command="onCommand">
          <span class="user-info">
            {{ auth.user?.displayName || auth.user?.username }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  Folder,
  Menu as MenuIcon,
  OfficeBuilding,
  Odometer,
  Postcard,
  Setting,
  Switch,
  TrendCharts,
  User
} from '@element-plus/icons-vue'
import { fetchMyMenus } from '@/api/menu'
import type { MenuNode } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useCompanyStore } from '@/stores/company'

const auth = useAuthStore()
const company = useCompanyStore()
const route = useRoute()
const router = useRouter()

/** 菜单由服务端按权限点下发（见 GET /api/v1/menus），这里只负责把图标名映射成组件 */
const ICONS: Record<string, unknown> = {
  Odometer,
  OfficeBuilding,
  User,
  Switch,
  Folder,
  TrendCharts,
  Setting,
  Postcard
}

const menus = ref<MenuNode[]>([])

function iconOf(name: string | null) {
  return (name && ICONS[name]) || MenuIcon
}

onMounted(async () => {
  company.load()
  try {
    menus.value = await fetchMyMenus()
  } catch {
    // 401 由 http 拦截器统一处理为跳登录，其余错误保持空菜单，避免整页白屏
    menus.value = []
  }
})

const activePath = computed(() => {
  if (route.path.startsWith('/employees/')) return '/employees'
  return route.path
})

const currentTitle = computed(() => {
  for (const m of menus.value) {
    if (m.path && m.path === route.path) return m.title
    const child = m.children.find((c) => !!c.path && route.path.startsWith(c.path))
    if (child) return child.title
  }
  return ''
})

async function onCommand(command: string) {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    await auth.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background-color: #001529;
}

.logo {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 60px;
  padding: 0 12px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}

.logo-img {
  max-height: 36px;
  max-width: 196px;
  object-fit: contain;
}

.logo-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu {
  border-right: none;
}

.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.user-info {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
}

.main {
  background-color: #f5f7fa;
  padding: 16px;
}
</style>
