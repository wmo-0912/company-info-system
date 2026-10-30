import { apiGet } from './http'
import type { AuditLog, PageResponse } from './types'

export interface AuditPageParams {
  page: number
  pageSize: number
  actorName?: string
  resourceType?: string
  startDate?: string
  endDate?: string
}

export function pageAuditLogs(params: AuditPageParams) {
  return apiGet<PageResponse<AuditLog>>('/audit-logs', { ...params })
}
