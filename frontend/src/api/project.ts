import { apiDelete, apiGet, apiPatch, apiPost, apiPut } from './http'
import type { PageResponse, ProjectMemberVO, ProjectVO } from './types'

export interface ProjectBody {
  projectCode: string
  projectName: string
  managerEmployeeId: number
  description?: string | null
}

export function pageProjects(params: { page: number; pageSize: number; keyword?: string; status?: string }) {
  return apiGet<PageResponse<ProjectVO>>('/projects', { ...params })
}

export function fetchMyTeams() {
  return apiGet<ProjectVO[]>('/projects/my-teams')
}

export function getProject(id: number) {
  return apiGet<ProjectVO>(`/projects/${id}`)
}

export function createProject(body: ProjectBody) {
  return apiPost<ProjectVO>('/projects', body)
}

export function updateProject(id: number, body: ProjectBody) {
  return apiPut<ProjectVO>(`/projects/${id}`, body)
}

export function fetchProjectMembers(id: number) {
  return apiGet<ProjectMemberVO[]>(`/projects/${id}/members`)
}

export function updateProjectStatus(id: number, status: 'ACTIVE' | 'CLOSED') {
  return apiPatch<ProjectVO>(`/projects/${id}/status`, { status })
}

export function addProjectMember(id: number, employeeId: number, memberRole?: string, joinedAt?: string) {
  return apiPost<ProjectMemberVO>(`/projects/${id}/members`, { employeeId, memberRole, joinedAt })
}

/** leftAt 为空时后端取当天；历史项目可补录实际离开日期 */
export function removeProjectMember(id: number, employeeId: number, leftAt?: string) {
  return apiDelete<void>(`/projects/${id}/members/${employeeId}`, leftAt ? { leftAt } : undefined)
}
