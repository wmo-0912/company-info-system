import { apiGet, apiPut } from './http'
import type { EmployeeDetail, EmploymentRecordVO } from './types'

export interface ProfileUpdateBody {
  phone?: string | null
  email?: string | null
  address?: string | null
  emergencyContactName?: string | null
  emergencyContactPhone?: string | null
  educationCode?: string | null
}

export function fetchProfile() {
  return apiGet<EmployeeDetail>('/profile')
}

export function updateProfile(body: ProfileUpdateBody) {
  return apiPut<EmployeeDetail>('/profile', body)
}

export function fetchMyEmploymentRecords() {
  return apiGet<EmploymentRecordVO[]>('/profile/employment-records')
}
