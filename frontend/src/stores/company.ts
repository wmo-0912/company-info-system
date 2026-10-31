import { defineStore } from 'pinia'
import { fetchCompanyInfo } from '@/api/system'

interface CompanyState {
  name: string
  logoUrl: string | null
  loaded: boolean
}

/** 默认标题：未取到系统配置时与登录页保持一致（后端兜底文案相同） */
export const DEFAULT_COMPANY_NAME = '公司信息管理系统'

/**
 * 公司信息（名称 / Logo）由管理员在「系统配置」里维护。
 * 登录页在无 token 时也要展示，所以单独存一份，避免刷新页面时闪回硬编码标题。
 */
export const useCompanyStore = defineStore('company', {
  state: (): CompanyState => ({
    name: DEFAULT_COMPANY_NAME,
    logoUrl: null,
    loaded: false
  }),
  actions: {
    async load(force = false) {
      if (this.loaded && !force) return
      try {
        const info = await fetchCompanyInfo()
        this.name = info.name || DEFAULT_COMPANY_NAME
        this.logoUrl = info.logoUrl || null
        this.loaded = true
      } catch {
        // 公开接口异常时退回默认名称，不阻塞登录页渲染
        this.name = DEFAULT_COMPANY_NAME
        this.logoUrl = null
      }
      this.applyDocumentTitle()
    },
    applyDocumentTitle() {
      document.title = this.name
    }
  }
})
