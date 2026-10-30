import { apiGet, apiPost, apiPut } from './http'
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
