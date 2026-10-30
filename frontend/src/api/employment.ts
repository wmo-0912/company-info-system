import { apiGet, apiPost } from './http'
import type { ChangeRequestVO, PageResponse } from './types'

export interface ChangeRequestPageParams {
  page: number
  pageSize: number
  status?: string
  changeType?: string
  employeeId?: number
}

export interface ChangeRequestCreateBody {
  employeeId: number
  changeType: string
  effectiveDate: string
  reason?: string | null
  payload?: Record<string, unknown> | null
}

export function pageChangeRequests(params: ChangeRequestPageParams) {
  return apiGet<PageResponse<ChangeRequestVO>>('/employment/requests', { ...params })
}

export function getChangeRequest(id: number) {
  return apiGet<ChangeRequestVO>(`/employment/requests/${id}`)
}

export function createChangeRequest(body: ChangeRequestCreateBody) {
  return apiPost<ChangeRequestVO>('/employment/requests', body)
}

export function submitChangeRequest(id: number) {
  return apiPost<ChangeRequestVO>(`/employment/requests/${id}/submit`)
}

export function processChangeRequest(id: number, decision: string, comment?: string) {
  return apiPost<ChangeRequestVO>(`/employment/requests/${id}/process`, { decision, comment })
}

export function cancelChangeRequest(id: number) {
  return apiPost<ChangeRequestVO>(`/employment/requests/${id}/cancel`)
}
