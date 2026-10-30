<template>
  <div class="page-card">
    <div class="page-toolbar">
      <div class="spacer" />
      <el-button v-if="auth.has('role:manage')" type="primary" @click="openDialog()">新建角色</el-button>
    </div>

    <el-table v-loading="loading" :data="records">
      <el-table-column prop="roleName" label="角色名称" width="160" />
      <el-table-column prop="roleCode" label="角色编码" width="160" />
      <el-table-column label="数据范围" width="120">
        <template #default="{ row }">{{ dictLabel('dataScope', row.dataScope) }}</template>
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createRole, fetchPermissionTree, listRoles, saveRolePermissions, setRoleEnabled, updateRole } from '@/api/role'
import type { PermissionTreeNode, RoleVO } from '@/api/types'
import { dictLabel, dictOptions } from '@/utils/dict'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const scopeOptions = dictOptions('dataScope')
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

const form = reactive({ roleCode: '', roleName: '', dataScope: 'SELF', description: '' })
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

function openDialog(row?: RoleVO) {
  editing.value = row || null
  if (row) {
    Object.assign(form, {
      roleCode: row.roleCode,
      roleName: row.roleName,
      dataScope: row.dataScope,
      description: row.description || ''
    })
  } else {
    Object.assign(form, { roleCode: '', roleName: '', dataScope: 'SELF', description: '' })
  }
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
    if (editing.value) {
      await updateRole(editing.value.id, body)
      ElMessage.success('角色已更新')
    } else {
      await createRole(body)
      ElMessage.success('角色已创建')
    }
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
</style>
