import { apiGet, apiPatch, apiPost, apiPut } from './http'
import type { PageResponse, PerformanceReviewVO } from './types'

export interface ReviewPageParams {
  page: number
  pageSize: number
  periodType?: string
  periodKey?: string
  status?: string
}

export interface ReviewBody {
  employeeId: number
  projectTeamId?: number | null
  periodType: string
  periodKey: string
  score?: number | null
  rating?: string | null
  comment?: string | null
}

export function pageReviews(params: ReviewPageParams) {
  return apiGet<PageResponse<PerformanceReviewVO>>('/performance-reviews', { ...params })
}

export function fetchMyReviews(page: number, pageSize: number) {
  return apiGet<PageResponse<PerformanceReviewVO>>('/performance-reviews/my', { page, pageSize })
}

export function getReview(id: number) {
  return apiGet<PerformanceReviewVO>(`/performance-reviews/${id}`)
}

export function createReview(body: ReviewBody) {
  return apiPost<PerformanceReviewVO>('/performance-reviews', body)
}

export function updateReview(id: number, body: ReviewBody) {
  return apiPut<PerformanceReviewVO>(`/performance-reviews/${id}`, body)
}

export function submitReview(id: number) {
  return apiPost<PerformanceReviewVO>(`/performance-reviews/${id}/submit`)
}

/** 人事复核退回，原因只进审计日志 */
export function returnReview(id: number, reason?: string) {
  return apiPost<PerformanceReviewVO>(`/performance-reviews/${id}/return`, { reason: reason || null })
}

export interface PerformancePeriodVO {
  periodKey: string
  locked: boolean
  lockedAt?: string | null
  lockedBy?: number | null
}

/** 已登记的绩效周期（含锁定状态） */
export function fetchPeriods() {
  return apiGet<PerformancePeriodVO[]>('/performance-periods')
}

/** 锁定/解锁某个绩效周期：锁定后该周期不能新增、修改、提交评价 */
export function setPeriodLocked(periodKey: string, locked: boolean) {
  return apiPatch<PerformancePeriodVO>(`/performance-periods/${encodeURIComponent(periodKey)}/lock`, { locked })
}
