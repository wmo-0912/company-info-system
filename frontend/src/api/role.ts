import { apiGet, apiPost, apiPut } from './http'
import type { PermissionTreeNode, RoleVO } from './types'

export interface RoleBody {
  roleCode: string
  roleName: string
  description?: string | null
  dataScope: string
}

export function listRoles() {
  return apiGet<RoleVO[]>('/roles')
}

export function createRole(body: RoleBody) {
  return apiPost<RoleVO>('/roles', body)
}

export function updateRole(id: number, body: RoleBody) {
  return apiPut<RoleVO>(`/roles/${id}`, body)
}

export function saveRolePermissions(id: number, permissionIds: number[]) {
  return apiPut<void>(`/roles/${id}/permissions`, { permissionIds })
}

export function fetchPermissionTree() {
  return apiGet<PermissionTreeNode[]>('/permissions/tree')
}
