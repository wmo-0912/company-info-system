type TagType = 'success' | 'warning' | 'info' | 'primary' | 'danger'

const maps: Record<string, Record<string, string>> = {
  employeeStatus: {
    PENDING: '待入职',
    PROBATION: '试用期',
    REGULAR: '正式',
    TRANSFERRED: '已调岗',
    RESIGNED: '已离职'
  },
  changeType: {
    ONBOARD: '入职',
    REGULARIZE: '转正',
    TRANSFER: '调岗',
    RESIGN: '离职'
  },
  requestStatus: {
    DRAFT: '草稿',
    SUBMITTED: '已提交',
    APPROVED: '已批准',
    REJECTED: '已驳回',
    CANCELLED: '已取消'
  },
  reviewStatus: {
    DRAFT: '草稿',
    SUBMITTED: '已提交',
    RETURNED: '已退回'
  },
  periodType: {
    MONTHLY: '月度',
    QUARTERLY: '季度'
  },
  dataScope: {
    ALL: '全部数据',
    DEPARTMENT: '本部门',
    PROJECT: '本项目组',
    SELF: '仅本人'
  },
  userStatus: {
    ACTIVE: '启用',
    DISABLED: '停用',
    LOCKED: '锁定'
  },
  commonStatus: {
    ACTIVE: '启用',
    DISABLED: '停用'
  },
  projectStatus: {
    ACTIVE: '进行中',
    CLOSED: '已关闭'
  },
  auditResult: {
    SUCCESS: '成功',
    FAILURE: '失败'
  },
  gender: {
    MALE: '男',
    FEMALE: '女'
  }
}

const tagTypes: Record<string, Record<string, TagType>> = {
  employeeStatus: {
    PENDING: 'info',
    PROBATION: 'warning',
    REGULAR: 'success',
    TRANSFERRED: 'primary',
    RESIGNED: 'danger'
  },
  requestStatus: {
    DRAFT: 'info',
    SUBMITTED: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    CANCELLED: 'info'
  },
  reviewStatus: {
    DRAFT: 'info',
    SUBMITTED: 'success',
    RETURNED: 'warning'
  },
  userStatus: {
    ACTIVE: 'success',
    DISABLED: 'info',
    LOCKED: 'danger'
  },
  commonStatus: {
    ACTIVE: 'success',
    DISABLED: 'info'
  },
  projectStatus: {
    ACTIVE: 'success',
    CLOSED: 'info'
  },
  auditResult: {
    SUCCESS: 'success',
    FAILURE: 'danger'
  }
}

export function dictLabel(group: string, value: string | null | undefined): string {
  if (value == null || value === '') return '-'
  return maps[group]?.[value] ?? value
}

export function dictTagType(group: string, value: string | null | undefined): TagType {
  if (value == null) return 'info'
  return tagTypes[group]?.[value] ?? 'info'
}

export function dictOptions(group: string): { value: string; label: string }[] {
  return Object.entries(maps[group] || {}).map(([value, label]) => ({ value, label }))
}
