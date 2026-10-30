import { defineStore } from 'pinia'
import { fetchMe, login, logoutApi } from '@/api/auth'
import type { CurrentUser } from '@/api/types'

interface AuthState {
  token: string
  user: CurrentUser | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem('token') || '',
    user: null
  }),
  getters: {
    permissions: (state): Set<string> => new Set(state.user?.permissions || []),
    isLoggedIn: (state) => !!state.token
  },
  actions: {
    has(perm: string): boolean {
      return this.permissions.has(perm)
    },
    async login(username: string, password: string) {
      const res = await login(username, password)
      this.token = res.token
      localStorage.setItem('token', res.token)
      this.user = res.user
    },
    async loadMe() {
      this.user = await fetchMe()
    },
    async logout() {
      try {
        await logoutApi()
      } catch {
        // 忽略退出接口异常，本地照常清理
      }
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
    }
  }
})
