<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">公司信息管理系统</div>
      <el-menu :default-active="activePath" router background-color="#001529" text-color="#a6adb4"
        active-text-color="#ffffff" class="menu">
        <template v-for="item in visibleMenus" :key="item.title">
          <el-sub-menu v-if="item.children" :index="item.title">
            <template #title>
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item v-for="child in item.children" :key="child.path" :index="child.path">
              {{ child.title }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-title">{{ currentTitle }}</div>
        <el-dropdown trigger="click" @command="onCommand">
          <span class="user-info" title="右键进入个人中心" @contextmenu.prevent="openProfile">
            <el-icon class="user-avatar"><UserFilled /></el-icon>
            {{ auth.user?.displayName || auth.user?.username }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
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
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  Folder,
  OfficeBuilding,
  Odometer,
  Setting,
  Switch,
  TrendCharts,
  User,
  UserFilled
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

interface MenuItem {
  title: string
  path?: string
  icon?: unknown
  children?: { title: string; path: string }[]
}

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const menus: (MenuItem & { perm?: string })[] = [
  { title: '首页仪表盘', path: '/', icon: Odometer, perm: 'dashboard:view' },
  { title: '组织架构', path: '/departments', icon: OfficeBuilding, perm: 'department:read' },
  { title: '员工管理', path: '/employees', icon: User, perm: 'employee:read' },
  { title: '入转调离', path: '/employment', icon: Switch, perm: 'employment:submit' },
  { title: '项目组', path: '/projects', icon: Folder, perm: 'project:read' },
  { title: '绩效考核', path: '/performance', icon: TrendCharts, perm: 'performance:read' }
]

const systemChildren = [
  { title: '用户管理', path: '/system/users', perm: 'user:read' },
  { title: '角色管理', path: '/system/roles', perm: 'role:read' },
  { title: '数据字典', path: '/system/dictionaries', perm: 'dictionary:read' },
  { title: '系统配置', path: '/system/config', perm: 'system:config:read' },
  { title: '审计日志', path: '/system/audit-logs', perm: 'audit:read' }
]

const visibleMenus = computed<MenuItem[]>(() => {
  const result: MenuItem[] = []
  for (const m of menus) {
    if (m.perm && auth.has(m.perm)) {
      result.push({ title: m.title, path: m.path, icon: m.icon, children: m.children })
    }
  }
  const sysItems = systemChildren.filter((c) => auth.has(c.perm))
  if (sysItems.length) {
    result.push({
      title: '系统管理',
      icon: Setting,
      children: sysItems.map((c) => ({ title: c.title, path: c.path }))
    })
  }
  return result
})

const activePath = computed(() => {
  if (route.path.startsWith('/employees/')) return '/employees'
  return route.path
})

const currentTitle = computed(() => {
  for (const m of visibleMenus.value) {
    if (m.path === route.path) return m.title
    const child = m.children?.find((c) => route.path.startsWith(c.path))
    if (child) return child.title
  }
  return ''
})

async function onCommand(command: string) {
  if (command === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    await auth.logout()
    router.push('/login')
  }
}

/** 个人中心不在导航栏展示，只能通过右键右上角用户图标进入 */
function openProfile() {
  if (auth.has('profile:read')) {
    router.push('/profile')
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
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
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

.user-avatar {
  font-size: 18px;
}

.main {
  background-color: #f5f7fa;
  padding: 16px;
}
</style>
