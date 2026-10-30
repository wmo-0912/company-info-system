export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  timestamp: string
}

export interface PageResponse<T> {
  records: T[]
  page: number
  pageSize: number
  total: number
}

export interface CurrentUser {
  id: number
  username: string
  displayName: string
  employeeId: number | null
  roles: string[]
  permissions: string[]
  dataScope: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresAt: string
  user: CurrentUser
}

export interface UserVO {
  id: number
  username: string
  employeeId: number | null
  employeeName: string | null
  status: string
  lastLoginAt: string | null
  roles: string[]
  roleIds: number[]
}

export interface RoleVO {
  id: number
  roleCode: string
  roleName: string
  description: string | null
  dataScope: string
  enabled: boolean
  permissionIds: number[]
}

export interface PermissionTreeNode {
  id: number
  permissionCode: string
  permissionName: string
  permissionType: string
  parentId: number | null
  routePath: string | null
  icon: string | null
  sortOrder: number | null
  children: PermissionTreeNode[]
}

export interface DepartmentVO {
  id: number
  departmentCode: string
  departmentName: string
  parentId: number | null
  managerEmployeeId: number | null
  managerEmployeeName: string | null
  description: string | null
  status: string
  children: DepartmentVO[] | null
}

export interface EmployeeListItem {
  id: number
  employeeNo: string
  employeeName: string
  gender: string | null
  departmentId: number
  departmentName: string | null
  positionName: string | null
  positionLevelCode: string | null
  employmentStatus: string
  hireDate: string | null
}

export interface EmployeeDetail {
  id: number
  employeeNo: string
  employeeName: string
  gender: string | null
  birthDate: string | null
  phone: string | null
  email: string | null
  address: string | null
  emergencyContactName: string | null
  emergencyContactPhone: string | null
  educationCode: string | null
  departmentId: number
  departmentName: string | null
  positionName: string | null
  positionLevelCode: string | null
  managerEmployeeId: number | null
  managerEmployeeName: string | null
  hireDate: string | null
  employmentStatus: string
  resignationDate: string | null
  remark: string | null
}

export interface EmploymentRecordVO {
  id: number
  employeeId: number
  changeType: string
  effectiveDate: string
  oldDepartmentId: number | null
  oldDepartmentName: string | null
  newDepartmentId: number | null
  newDepartmentName: string | null
  oldPositionName: string | null
  newPositionName: string | null
  reason: string | null
  operatorName: string | null
}

export interface ChangeRequestVO {
  id: number
  requestNo: string
  employeeId: number
  employeeName: string
  employeeNo: string
  changeType: string
  status: string
  effectiveDate: string
  payload: Record<string, unknown> | null
  reason: string | null
  submittedBy: number | null
  submittedByName: string | null
  processedBy: number | null
  processedByName: string | null
  processedAt: string | null
  processComment: string | null
  createdAt: string
}

export interface ProjectVO {
  id: number
  projectCode: string
  projectName: string
  managerEmployeeId: number
  managerEmployeeName: string | null
  description: string | null
  status: string
  memberCount: number
}

export interface ProjectMemberVO {
  id: number
  projectTeamId: number
  employeeId: number
  employeeNo: string
  employeeName: string
  departmentName: string | null
  memberRole: string | null
  joinedAt: string | null
  leftAt: string | null
}

export interface PerformanceReviewVO {
  id: number
  employeeId: number
  employeeName: string
  employeeNo: string
  reviewerEmployeeId: number
  reviewerName: string | null
  projectTeamId: number | null
  projectTeamName: string | null
  periodType: string
  periodKey: string
  score: number | null
  rating: string | null
  comment: string | null
  status: string
  submittedAt: string | null
  createdAt: string
}

export interface DictionaryType {
  id: number
  dictCode: string
  dictName: string
  enabled: boolean
}

export interface DictionaryItem {
  id: number
  dictTypeId: number
  itemCode: string
  itemLabel: string
  sortOrder: number | null
  enabled: boolean
}

export interface SysConfig {
  id: number
  configKey: string
  configValue: string | null
  description: string | null
  updatedBy: number | null
  updatedAt: string | null
}

export interface AuditLog {
  id: number
  actorUserId: number | null
  actorName: string | null
  action: string
  resourceType: string
  resourceId: string | null
  result: string
  ipAddress: string | null
  summary: string | null
  createdAt: string
}

export interface DashboardSummary {
  employeeCount: number
  probationCount: number
  recentHireCount: number
  pendingChangeCount: number
  myTeamCount: number
  myDraftReviewCount: number
}
