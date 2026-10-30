import { apiGet, apiPatch, apiPost, apiPut } from './http'
import type {
  ChangeRequestVO,
  EmployeeDetail,
  EmployeeListItem,
  EmploymentRecordVO,
  PageResponse
} from './types'

export interface EmployeePageParams {
  page: number
  pageSize: number
  keyword?: string
  departmentId?: number
  status?: string
}

export interface EmployeeCreateBody {
  employeeNo: string
  employeeName: string
  gender?: string | null
  birthDate?: string | null
  phone?: string | null
  email?: string | null
  address?: string | null
  emergencyContactName?: string | null
  emergencyContactPhone?: string | null
  educationCode?: string | null
  departmentId: number
  positionName?: string | null
  positionLevelCode?: string | null
  managerEmployeeId?: number | null
  hireDate?: string | null
  remark?: string | null
}

export interface EmployeeUpdateBody {
  employeeName: string
  gender?: string | null
  birthDate?: string | null
  phone?: string | null
  email?: string | null
  address?: string | null
  emergencyContactName?: string | null
  emergencyContactPhone?: string | null
  educationCode?: string | null
  departmentId: number
  positionName?: string | null
  positionLevelCode?: string | null
  managerEmployeeId?: number | null
  hireDate?: string | null
  remark?: string | null
}

export function pageEmployees(params: EmployeePageParams) {
  return apiGet<PageResponse<EmployeeListItem>>('/employees', { ...params })
}

export function getEmployee(id: number) {
  return apiGet<EmployeeDetail>(`/employees/${id}`)
}

export function createEmployee(body: EmployeeCreateBody) {
  return apiPost<EmployeeDetail>('/employees', body)
}

export function updateEmployee(id: number, body: EmployeeUpdateBody) {
  return apiPut<EmployeeDetail>(`/employees/${id}`, body)
}

export function archiveEmployee(id: number) {
  return apiPatch<void>(`/employees/${id}/archive`)
}

export function fetchEmployeeRecords(id: number) {
  return apiGet<EmploymentRecordVO[]>(`/employees/${id}/employment-records`)
}

export function fetchEmployeeChangeRequests(id: number, page: number, pageSize: number) {
  return apiGet<PageResponse<ChangeRequestVO>>(`/employees/${id}/change-requests`, { page, pageSize })
}
