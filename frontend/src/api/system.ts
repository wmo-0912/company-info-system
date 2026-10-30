import { apiGet, apiPut } from './http'
import type { CompanyInfo, DashboardSummary, SysConfig } from './types'

export function fetchDashboardSummary() {
  return apiGet<DashboardSummary>('/dashboard/summary')
}

/** 公开接口：登录页在拿到 token 之前就需要公司名称与 Logo */
export function fetchCompanyInfo() {
  return apiGet<CompanyInfo>('/public/company-info')
}

export function listConfigs() {
  return apiGet<SysConfig[]>('/system/config')
}

export function upsertConfig(configKey: string, configValue: string | null, description?: string | null) {
  return apiPut<SysConfig>('/system/config', { configKey, configValue, description })
}
