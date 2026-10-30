<template>
  <div class="page-card">
    <div class="page-toolbar">
      <div class="spacer" />
      <el-button v-if="auth.has('role:manage')" type="primary" @click="openDialog()">新建角色</el-button>
    </div>

    <el-table v-loading="loading" :data="records">
      <el-table-column prop="roleName" label="角色名称" width="160" />
      <el-table-column prop="roleCode" label="角色编码" width="160" />
      <el-table-column label="数据范围" width="200">
        <template #default="{ row }">
          {{ dictLabel('dataScope', row.dataScope) }}
          <el-tag v-if="overrideCount(row)" size="small" type="info" style="margin-left: 6px">
            {{ overrideCount(row) }} 个模块单独配置
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="isBuiltIn(row.roleCode)" type="success">启用</el-tag>
          <el-switch v-else :model-value="row.enabled" :loading="toggling === row.id"
            @change="(val: any) => onToggleEnabled(row, !!val)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-if="auth.has('role:manage')" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button v-if="auth.has('role:manage') || auth.has('permission:manage')" link type="primary"
            @click="openPerms(row)">分配权限</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无角色" />
      </template>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑角色' : '新建角色'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" :disabled="!!editing && isBuiltIn(editing.roleCode)" />
          <div v-if="editing && isBuiltIn(editing.roleCode)" class="form-hint">内置角色的编码不可修改</div>
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item label="数据范围" prop="dataScope">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option v-for="o in scopeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="模块范围">
          <div class="module-scopes">
            <div v-if="isAdminForm" class="form-hint">管理员角色的数据范围固定为“全部数据”，不能按模块收窄</div>
            <div v-for="m in MODULES" :key="m.value" class="module-row">
              <span class="module-label">{{ m.label }}</span>
              <el-select v-model="form.moduleScopes[m.value]" :disabled="isAdminForm" size="small"
                style="width: 170px">
                <el-option label="沿用角色默认" value="" />
                <el-option v-for="o in scopeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </div>
            <div class="form-hint">留空表示沿用上面的默认数据范围；例如“人事默认看全部、绩效只能看本部门”</div>
          </div>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permVisible" :title="`分配权限 - ${currentRole?.roleName || ''}`" width="480px" destroy-on-close>
      <el-tree ref="permTreeRef" :data="permTree" show-checkbox node-key="id"
        :props="{ label: 'permissionName', children: 'children' }" :default-checked-keys="checkedPermIds" />
      <template #footer>
        <el-button @click="permVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSavePerms">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createRole, fetchPermissionTree, listRoles, saveRoleModuleScopes, saveRolePermissions, setRoleEnabled, updateRole } from '@/api/role'
import type { PermissionTreeNode, RoleVO } from '@/api/types'
import { dictLabel, dictOptions } from '@/utils/dict'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const scopeOptions = dictOptions('dataScope')
/** 可按模块单独配置数据范围的业务模块，需与后端 DataModule 枚举一致 */
const MODULES = [
  { value: 'EMPLOYEE', label: '员工档案' },
  { value: 'DEPARTMENT', label: '组织架构' },
  { value: 'PROJECT', label: '项目组' },
  { value: 'PERFORMANCE', label: '绩效考核' },
  { value: 'EMPLOYMENT', label: '入转调离' }
]
/** 内置角色：初始化时创建，被代码与菜单依赖，后端不允许停用或改编码 */
const BUILT_IN_ROLES = ['ADMIN', 'HR', 'PROJECT_MANAGER', 'EMPLOYEE']
const toggling = ref<number | null>(null)

const records = ref<RoleVO[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const permVisible = ref(false)
const saving = ref(false)
const editing = ref<RoleVO | null>(null)
const currentRole = ref<RoleVO | null>(null)
const formRef = ref<FormInstance>()
const permTreeRef = ref()
const permTree = ref<PermissionTreeNode[]>([])
const checkedPermIds = ref<number[]>([])

const form = reactive({
  roleCode: '',
  roleName: '',
  dataScope: 'SELF',
  description: '',
  moduleScopes: emptyModuleScopes()
})

function emptyModuleScopes(): Record<string, string> {
  const result: Record<string, string> = {}
  for (const m of MODULES) result[m.value] = ''
  return result
}
const formRules: FormRules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  dataScope: [{ required: true, message: '请选择数据范围', trigger: 'change' }]
}

async function load() {
  loading.value = true
  try {
    records.value = await listRoles()
  } finally {
    loading.value = false
  }
}

const isAdminForm = computed(() => (editing.value?.roleCode || form.roleCode) === 'ADMIN')

function overrideCount(row: RoleVO) {
  return Object.keys(row.moduleScopes || {}).length
}

function openDialog(row?: RoleVO) {
  editing.value = row || null
  const scopes = emptyModuleScopes()
  if (row) {
    Object.assign(form, {
      roleCode: row.roleCode,
      roleName: row.roleName,
      dataScope: row.dataScope,
      description: row.description || ''
    })
    for (const [module, scope] of Object.entries(row.moduleScopes || {})) {
      if (module in scopes) scopes[module] = scope
    }
  } else {
    Object.assign(form, { roleCode: '', roleName: '', dataScope: 'SELF', description: '' })
  }
  form.moduleScopes = scopes
  dialogVisible.value = true
}

function isBuiltIn(code: string) {
  return BUILT_IN_ROLES.includes(code)
}

async function onToggleEnabled(row: RoleVO, enabled: boolean) {
  toggling.value = row.id
  try {
    await setRoleEnabled(row.id, enabled)
    ElMessage.success(enabled ? '角色已启用' : '角色已停用，相关用户下次请求即失去该角色权限')
    load()
  } finally {
    toggling.value = null
  }
}

async function onSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const body = {
      roleCode: form.roleCode,
      roleName: form.roleName,
      dataScope: form.dataScope,
      description: form.description || null
    }
    let roleId: number
    if (editing.value) {
      await updateRole(editing.value.id, body)
      roleId = editing.value.id
    } else {
      const created = await createRole(body)
      roleId = created.id
    }
    const overrides: Record<string, string> = {}
    for (const [module, scope] of Object.entries(form.moduleScopes)) {
      if (scope) overrides[module] = scope
    }
    await saveRoleModuleScopes(roleId, overrides)
    ElMessage.success(editing.value ? '角色已更新' : '角色已创建')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

function leafIds(nodes: PermissionTreeNode[], ids: Set<number>): Set<number> {
  const result = new Set<number>()
  for (const n of nodes) {
    if (ids.has(n.id) && (!n.children || !n.children.length)) {
      result.add(n.id)
    }
    for (const id of leafIds(n.children || [], ids)) result.add(id)
  }
  return result
}

async function openPerms(row: RoleVO) {
  currentRole.value = row
  if (!permTree.value.length) {
    permTree.value = await fetchPermissionTree()
  }
  checkedPermIds.value = [...leafIds(permTree.value, new Set(row.permissionIds))]
  permVisible.value = true
}

async function onSavePerms() {
  const checked: number[] = permTreeRef.value?.getCheckedKeys() || []
  const half: number[] = permTreeRef.value?.getHalfCheckedKeys() || []
  const ids = [...checked, ...half]
  if (!ids.length) {
    ElMessage.warning('请至少勾选一个权限')
    return
  }
  saving.value = true
  try {
    await saveRolePermissions(currentRole.value!.id, ids)
    ElMessage.success('权限已保存')
    permVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.form-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.module-scopes {
  width: 100%;
}

.module-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.module-label {
  width: 76px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
</style>
