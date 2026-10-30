import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    children: [
      { path: '', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { perm: 'dashboard:view' } },
      { path: 'departments', name: 'Departments', component: () => import('@/views/Departments.vue'), meta: { perm: 'department:read' } },
      { path: 'employees', name: 'Employees', component: () => import('@/views/Employees.vue'), meta: { perm: 'employee:read' } },
      { path: 'employees/:id', name: 'EmployeeDetail', component: () => import('@/views/EmployeeDetail.vue'), meta: { perm: 'employee:read' } },
      { path: 'employment', name: 'Employment', component: () => import('@/views/Employment.vue'), meta: { perm: 'employment:submit' } },
      { path: 'projects', name: 'Projects', component: () => import('@/views/Projects.vue'), meta: { perm: 'project:read' } },
      { path: 'performance', name: 'Performance', component: () => import('@/views/Performance.vue'), meta: { perm: 'performance:read' } },
      { path: 'system/users', name: 'SystemUsers', component: () => import('@/views/system/Users.vue'), meta: { perm: 'user:read' } },
      { path: 'system/roles', name: 'SystemRoles', component: () => import('@/views/system/Roles.vue'), meta: { perm: 'role:read' } },
      { path: 'system/dictionaries', name: 'SystemDictionaries', component: () => import('@/views/system/Dictionaries.vue'), meta: { perm: 'dictionary:read' } },
      { path: 'system/config', name: 'SystemConfig', component: () => import('@/views/system/Config.vue'), meta: { perm: 'system:config:read' } },
      { path: 'system/audit-logs', name: 'AuditLogs', component: () => import('@/views/system/AuditLogs.vue'), meta: { perm: 'audit:read' } },
      { path: 'profile', name: 'Profile', component: () => import('@/views/Profile.vue'), meta: { perm: 'profile:read' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (auth.isLoggedIn) {
      return { path: '/' }
    }
    return true
  }
  if (!auth.isLoggedIn) {
    return { path: '/login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : {} }
  }
  if (!auth.user) {
    try {
      await auth.loadMe()
    } catch {
      return { path: '/login' }
    }
  }
  // 首登/重置密码后强制先改密：除个人中心外一律拦截
  if (auth.user?.mustChangePassword && to.path !== '/profile') {
    return { path: '/profile', query: { forcePasswordChange: '1' } }
  }
  const perm = to.meta.perm as string | undefined
  if (perm && !auth.has(perm)) {
    return { path: '/' }
  }
  return true
})

export default router
