<template>
  <div class="page-card">
    <div class="page-toolbar">
      <el-input v-model="keyword" placeholder="搜索用户名" clearable style="width: 200px" @keyup.enter="reload"
        @clear="reload" />
      <el-select v-model="status" placeholder="状态筛选" clearable style="width: 130px" @change="reload">
        <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-button type="primary" @click="reload">查询</el-button>
      <div class="spacer" />
      <el-button v-if="auth.has('user:create')" type="primary" @click="openCreate">新建用户</el-button>
    </div>

    <el-table v-loading="loading" :data="records">
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="employeeName" label="关联员工" width="120">
        <template #default="{ row }">{{ row.employeeName || '-' }}</template>
      </el-table-column>
      <el-table-column label="角色">
        <template #default="{ row }">{{ row.roles.join('、') || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="dictTagType('userStatus', row.status)">
            {{ dictLabel('userStatus', row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最后登录" width="170">
        <template #default="{ row }">{{ formatTime(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button v-if="auth.has('user:disable') && row.status !== 'ACTIVE'" link type="success"
            @click="onStatus(row, 'ACTIVE')">启用</el-button>
          <el-button v-if="auth.has('user:disable') && row.status === 'ACTIVE'" link type="warning"
            @click="onStatus(row, 'DISABLED')">停用</el-button>
          <el-button v-if="auth.has('user:disable') && row.status !== 'LOCKED'" link type="danger"
            @click="onStatus(row, 'LOCKED')">锁定</el-button>
          <el-button v-if="auth.has('user:disable')" link type="danger" @click="openReset(row)">重置密码</el-button>
          <el-button v-if="auth.has('user:update')" link type="primary" @click="openRoles(row)">分配角色</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无用户" />
      </template>
    </el-table>
    <el-pagination v-if="total > 0" class="pager" layout="total, sizes, prev, pager, next" :total="total"
      v-model:page-size="pageSize" v-model:current-page="page" :page-sizes="[10, 20, 50]" @change="load" />

    <el-dialog v-model="createVisible" title="新建用户" width="480px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        初始密码由管理员线下交付给用户，系统不发送通知。
      </el-alert>
      <el-form ref="createRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password placeholder="8-64 位" />
        </el-form-item>
        <el-form-item label="关联员工">
          <el-select v-model="createForm.employeeId" clearable filterable style="width: 100%">
            <el-option v-for="e in employeeOptions" :key="e.id" :label="`${e.employeeName}（${e.employeeNo}）`"
              :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="createForm.roleIds" multiple style="width: 100%">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetVisible" :title="`重置密码 - ${resetTarget?.username || ''}`" width="440px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        留空则由系统生成一次性随机密码；重置后该用户下次登录必须先修改密码。
      </el-alert>
      <el-form ref="resetRef" :model="resetForm" :rules="resetRules" label-width="90px">
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="resetForm.newPassword" type="password" show-password placeholder="留空则自动生成" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onReset">确认重置</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rolesVisible" :title="`分配角色 - ${currentUser?.username || ''}`" width="420px" destroy-on-close>
      <el-select v-model="selectedRoleIds" multiple style="width: 100%">
        <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
      </el-select>
      <template #footer>
        <el-button @click="rolesVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onAssignRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { assignUserRoles, changeUserStatus, createUser, pageUsers, resetUserPassword } from '@/api/user'
import { listRoles } from '@/api/role'
import { pageEmployees } from '@/api/employee'
import type { EmployeeListItem, RoleVO, UserVO } from '@/api/types'
import { dictLabel, dictOptions, dictTagType } from '@/utils/dict'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const keyword = ref('')
const status = ref('')
const statusOptions = dictOptions('userStatus')
const records = ref<UserVO[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const createVisible = ref(false)
const rolesVisible = ref(false)
const saving = ref(false)
const createRef = ref<FormInstance>()
const createForm = reactive({
  username: '',
  password: '',
  employeeId: null as number | null,
  roleIds: [] as number[]
})
const createRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 64, message: '长度 3-64 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度需为 8-64 位', trigger: 'blur' }
  ],
  roleIds: [{ required: true, type: 'array', min: 1, message: '至少分配一个角色', trigger: 'change' }]
}

const roleOptions = ref<RoleVO[]>([])
const employeeOptions = ref<EmployeeListItem[]>([])
const currentUser = ref<UserVO | null>(null)
const selectedRoleIds = ref<number[]>([])

const resetVisible = ref(false)
const resetRef = ref<FormInstance>()
const resetTarget = ref<UserVO | null>(null)
const resetForm = reactive({ newPassword: '' })
const resetRules: FormRules = {
  newPassword: [
    {
      validator: (_rule, value: string, callback: (e?: Error) => void) => {
        if (!value) return callback()
        if (!/^(?=.*[A-Za-z])(?=.*\d).{8,64}$/.test(value)) {
          callback(new Error('8-64 位，需同时包含字母和数字'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function formatTime(t: string | null): string {
  return t ? t.replace('T', ' ').slice(0, 19) : '-'
}

async function load() {
  loading.value = true
  try {
    const res = await pageUsers({
      page: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
      status: status.value || undefined
    })
    records.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

async function ensureOptions() {
  if (!roleOptions.value.length && auth.has('role:read')) {
    roleOptions.value = await listRoles()
  }
  if (!employeeOptions.value.length && auth.has('employee:read')) {
    const res = await pageEmployees({ page: 1, pageSize: 100 })
    employeeOptions.value = res.records
  }
}

async function openCreate() {
  await ensureOptions()
  Object.assign(createForm, { username: '', password: '', employeeId: null, roleIds: [] })
  createVisible.value = true
}

async function onCreate() {
  await createRef.value?.validate()
  saving.value = true
  try {
    await createUser({
      username: createForm.username,
      password: createForm.password,
      employeeId: createForm.employeeId,
      roleIds: createForm.roleIds
    })
    ElMessage.success('用户已创建')
    createVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function onStatus(row: UserVO, target: string) {
  await ElMessageBox.confirm(
    `确定${dictLabel('userStatus', target)}用户「${row.username}」吗？`,
    '提示',
    { type: 'warning' }
  )
  await changeUserStatus(row.id, target)
  ElMessage.success('操作成功')
  load()
}

function openReset(row: UserVO) {
  resetTarget.value = row
  resetForm.newPassword = ''
  resetVisible.value = true
}

async function onReset() {
  await resetRef.value?.validate()
  saving.value = true
  try {
    const res = await resetUserPassword(resetTarget.value!.id, resetForm.newPassword || undefined)
    resetVisible.value = false
    await ElMessageBox.alert(
      `用户 ${res.username} 的新密码为：${res.initialPassword}\n请通过安全渠道线下告知本人，该密码只显示这一次。`,
      '密码已重置',
      { type: 'success', confirmButtonText: '我已记录' }
    )
    load()
  } finally {
    saving.value = false
  }
}

async function openRoles(row: UserVO) {
  await ensureOptions()
  currentUser.value = row
  selectedRoleIds.value = [...row.roleIds]
  rolesVisible.value = true
}

async function onAssignRoles() {
  if (!selectedRoleIds.value.length) {
    ElMessage.warning('至少分配一个角色')
    return
  }
  saving.value = true
  try {
    await assignUserRoles(currentUser.value!.id, selectedRoleIds.value)
    ElMessage.success('角色已分配')
    rolesVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
