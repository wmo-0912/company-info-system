<template>
  <div class="page-card">
    <div class="page-toolbar">
      <el-input v-model="keyword" placeholder="搜索项目名称/编码" clearable style="width: 220px" @keyup.enter="reload"
        @clear="reload" />
      <el-select v-model="status" placeholder="状态筛选" clearable style="width: 140px" @change="reload">
        <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-checkbox v-model="onlyMine" @change="reload">我负责的项目</el-checkbox>
      <div class="spacer" />
      <el-button v-if="auth.has('project:create')" type="primary" @click="openDialog()">新建项目组</el-button>
    </div>

    <el-table v-loading="loading" :data="records">
      <el-table-column prop="projectCode" label="编码" width="140" />
      <el-table-column prop="projectName" label="名称" />
      <el-table-column prop="managerEmployeeName" label="负责人" width="120" />
      <el-table-column prop="memberCount" label="成员数" width="90" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="dictTagType('commonStatus', row.status)">
            {{ dictLabel('commonStatus', row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-if="auth.has('project:member:manage')" link type="primary"
            @click="openMembers(row)">成员管理</el-button>
          <el-button v-if="auth.has('project:update')" link type="primary" @click="openDialog(row)">编辑</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无项目组" />
      </template>
    </el-table>
    <el-pagination v-if="!onlyMine && total > 0" class="pager" layout="total, sizes, prev, pager, next" :total="total"
      v-model:page-size="pageSize" v-model:current-page="page" :page-sizes="[10, 20, 50]" @change="load" />

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑项目组' : '新建项目组'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="项目编码" prop="projectCode">
          <el-input v-model="form.projectCode" />
        </el-form-item>
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="form.projectName" />
        </el-form-item>
        <el-form-item label="负责人" prop="managerEmployeeId">
          <el-select v-model="form.managerEmployeeId" filterable style="width: 100%">
            <el-option v-for="e in employeeOptions" :key="e.id" :label="`${e.employeeName}（${e.employeeNo}）`"
              :value="e.id" />
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

    <el-drawer v-model="memberVisible" :title="`成员管理 - ${currentProject?.projectName || ''}`" size="480px">
      <div class="member-add">
        <el-select v-model="newMemberId" filterable placeholder="选择员工" style="flex: 1">
          <el-option v-for="e in employeeOptions" :key="e.id" :label="`${e.employeeName}（${e.employeeNo}）`"
            :value="e.id" />
        </el-select>
        <el-input v-model="newMemberRole" placeholder="成员角色" style="width: 120px" />
        <el-button type="primary" :disabled="!newMemberId" @click="onAddMember">添加</el-button>
      </div>
      <el-table v-loading="memberLoading" :data="members">
        <el-table-column label="员工" width="150">
          <template #default="{ row }">{{ row.employeeName }}（{{ row.employeeNo }}）</template>
        </el-table-column>
        <el-table-column prop="departmentName" label="部门" />
        <el-table-column prop="memberRole" label="角色" width="90">
          <template #default="{ row }">{{ row.memberRole || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" @click="onRemoveMember(row)">移出</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无成员" />
        </template>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  addProjectMember,
  createProject,
  fetchMyTeams,
  fetchProjectMembers,
  pageProjects,
  removeProjectMember,
  updateProject
} from '@/api/project'
import { pageEmployees } from '@/api/employee'
import type { EmployeeListItem, ProjectMemberVO, ProjectVO } from '@/api/types'
import { dictLabel, dictOptions, dictTagType } from '@/utils/dict'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const keyword = ref('')
const status = ref('')
const onlyMine = ref(false)
const statusOptions = dictOptions('commonStatus')
const records = ref<ProjectVO[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const editing = ref<ProjectVO | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  projectCode: '',
  projectName: '',
  managerEmployeeId: null as number | null,
  description: ''
})
const formRules: FormRules = {
  projectCode: [{ required: true, message: '请输入项目编码', trigger: 'blur' }],
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  managerEmployeeId: [{ required: true, message: '请选择负责人', trigger: 'change' }]
}

const memberVisible = ref(false)
const currentProject = ref<ProjectVO | null>(null)
const members = ref<ProjectMemberVO[]>([])
const memberLoading = ref(false)
const newMemberId = ref<number | null>(null)
const newMemberRole = ref('')

const employeeOptions = ref<EmployeeListItem[]>([])

async function ensureEmployeeOptions() {
  if (!employeeOptions.value.length) {
    const res = await pageEmployees({ page: 1, pageSize: 100 })
    employeeOptions.value = res.records
  }
}

async function load() {
  loading.value = true
  try {
    if (onlyMine.value) {
      records.value = await fetchMyTeams()
      total.value = 0
    } else {
      const res = await pageProjects({
        page: page.value,
        pageSize: pageSize.value,
        keyword: keyword.value || undefined,
        status: status.value || undefined
      })
      records.value = res.records
      total.value = res.total
    }
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

async function openDialog(row?: ProjectVO) {
  editing.value = row || null
  await ensureEmployeeOptions()
  if (row) {
    Object.assign(form, {
      projectCode: row.projectCode,
      projectName: row.projectName,
      managerEmployeeId: row.managerEmployeeId,
      description: row.description || ''
    })
  } else {
    Object.assign(form, { projectCode: '', projectName: '', managerEmployeeId: null, description: '' })
  }
  dialogVisible.value = true
}

async function onSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const body = {
      projectCode: form.projectCode,
      projectName: form.projectName,
      managerEmployeeId: form.managerEmployeeId!,
      description: form.description || null
    }
    if (editing.value) {
      await updateProject(editing.value.id, body)
      ElMessage.success('项目组已更新')
    } else {
      await createProject(body)
      ElMessage.success('项目组已创建')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function openMembers(row: ProjectVO) {
  currentProject.value = row
  memberVisible.value = true
  await ensureEmployeeOptions()
  await loadMembers()
}

async function loadMembers() {
  if (!currentProject.value) return
  memberLoading.value = true
  try {
    members.value = await fetchProjectMembers(currentProject.value.id)
  } finally {
    memberLoading.value = false
  }
}

async function onAddMember() {
  if (!currentProject.value || !newMemberId.value) return
  await addProjectMember(currentProject.value.id, newMemberId.value, newMemberRole.value || undefined)
  ElMessage.success('成员已添加')
  newMemberId.value = null
  newMemberRole.value = ''
  loadMembers()
  load()
}

async function onRemoveMember(row: ProjectMemberVO) {
  await ElMessageBox.confirm(`确定将 ${row.employeeName} 移出项目组吗？`, '提示', { type: 'warning' })
  await removeProjectMember(currentProject.value!.id, row.employeeId)
  ElMessage.success('已移出')
  loadMembers()
  load()
}

onMounted(load)
</script>

<style scoped>
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.member-add {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
</style>
