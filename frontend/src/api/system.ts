import { apiGet, apiPut } from './http'
import type { DashboardSummary, SysConfig } from './types'

export function fetchDashboardSummary() {
  return apiGet<DashboardSummary>('/dashboard/summary')
}

export function listConfigs() {
  return apiGet<SysConfig[]>('/system/config')
}

export function upsertConfig(configKey: string, configValue: string | null, description?: string | null) {
  return apiPut<SysConfig>('/system/config', { configKey, configValue, description })
}
