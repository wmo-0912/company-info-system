import { apiGet, apiPatch, apiPost, apiPut } from './http'
import type { PageResponse, UserVO } from './types'

export interface UserPageParams {
  page: number
  pageSize: number
  keyword?: string
  status?: string
}

export interface UserCreateBody {
  username: string
  password: string
  employeeId?: number | null
  roleIds: number[]
}

export function pageUsers(params: UserPageParams) {
  return apiGet<PageResponse<UserVO>>('/users', { ...params })
}

export function getUser(id: number) {
  return apiGet<UserVO>(`/users/${id}`)
}

export function createUser(body: UserCreateBody) {
  return apiPost<UserVO>('/users', body)
}

export function updateUserEmployee(id: number, employeeId: number | null) {
  return apiPut<UserVO>(`/users/${id}`, { employeeId })
}

export function changeUserStatus(id: number, status: string) {
  return apiPatch<void>(`/users/${id}/status`, { status })
}

export function assignUserRoles(id: number, roleIds: number[]) {
  return apiPut<UserVO>(`/users/${id}/roles`, { roleIds })
}

export interface ResetPasswordResult {
  username: string
  initialPassword: string
}

/** 重置密码：不传 newPassword 时后端生成一次性随机密码，仅在返回值中出现一次 */
export function resetUserPassword(id: number, newPassword?: string) {
  return apiPost<ResetPasswordResult>(`/users/${id}/reset-password`, {
    newPassword: newPassword || null
  })
}
