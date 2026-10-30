import { apiGet, apiPatch, apiPost, apiPut } from './http'
import type { DepartmentVO, EmployeeListItem, PageResponse } from './types'

export interface DepartmentBody {
  departmentCode: string
  departmentName: string
  parentId?: number | null
  managerEmployeeId?: number | null
  description?: string | null
}

export function fetchDepartmentTree() {
  return apiGet<DepartmentVO[]>('/departments/tree')
}

export function pageDepartments(params: { page: number; pageSize: number; keyword?: string; status?: string }) {
  return apiGet<PageResponse<DepartmentVO>>('/departments', { ...params })
}

export function getDepartment(id: number) {
  return apiGet<DepartmentVO>(`/departments/${id}`)
}

export function createDepartment(body: DepartmentBody) {
  return apiPost<DepartmentVO>('/departments', body)
}

export function updateDepartment(id: number, body: DepartmentBody) {
  return apiPut<DepartmentVO>(`/departments/${id}`, body)
}

export function changeDepartmentStatus(id: number, status: string) {
  return apiPatch<void>(`/departments/${id}/status`, { status })
}

export function fetchDepartmentEmployees(id: number, page: number, pageSize: number, status?: string) {
  return apiGet<PageResponse<EmployeeListItem>>(`/departments/${id}/employees`, { page, pageSize, status })
}
