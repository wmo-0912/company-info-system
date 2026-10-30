import { apiDelete, apiGet, apiPost, apiPut } from './http'
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

export function addProjectMember(id: number, employeeId: number, memberRole?: string) {
  return apiPost<ProjectMemberVO>(`/projects/${id}/members`, { employeeId, memberRole })
}

export function removeProjectMember(id: number, employeeId: number) {
  return apiDelete<void>(`/projects/${id}/members/${employeeId}`)
}
