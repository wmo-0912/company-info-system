<template>
  <div class="page-card">
    <div class="page-toolbar">
      <el-button v-if="auth.has('department:create')" type="primary" @click="openDialog()">新建部门</el-button>
      <template v-if="selected">
        <el-button v-if="auth.has('department:update')" @click="openDialog(selected)">编辑当前部门</el-button>
        <el-button v-if="auth.has('department:disable')" :type="selected.status === 'ACTIVE' ? 'danger' : 'success'"
          @click="toggleStatus">
          {{ selected.status === 'ACTIVE' ? '停用当前部门' : '启用当前部门' }}
        </el-button>
      </template>
    </div>
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>部门树</template>
          <el-tree v-loading="treeLoading" :data="treeData" node-key="id" :props="treeProps" highlight-current
            default-expand-all @node-click="onNodeClick">
            <template #default="{ data }">
              <span>{{ data.departmentName }}
                <el-tag v-if="data.status !== 'ACTIVE'" type="info" size="small">停用</el-tag>
              </span>
            </template>
          </el-tree>
          <el-empty v-if="!treeLoading && !treeData.length" description="暂无部门" />
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div style="display: flex; align-items: center; justify-content: space-between;">
              <span>{{ selected ? `${selected.departmentName} - 员工列表` : '请选择左侧部门' }}</span>
              <el-radio-group v-if="selected" v-model="empStatus" size="small" @change="onStatusChange">
                <el-radio-button value="PROBATION,REGULAR,TRANSFERRED">在职员工</el-radio-button>
                <el-radio-button value="PENDING">待入职</el-radio-button>
                <el-radio-button value="">全部</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <el-table v-loading="empLoading" :data="employees">
            <el-table-column prop="employeeNo" label="编号" width="100" />
            <el-table-column prop="employeeName" label="姓名" />
            <el-table-column prop="positionName" label="岗位" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="dictTagType('employeeStatus', row.employmentStatus)">
                  {{ dictLabel('employeeStatus', row.employmentStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="hireDate" label="入职日期" width="120" />
          </el-table>
          <el-empty v-if="!empLoading && !employees.length" description="暂无员工" />
          <el-pagination v-if="empTotal > 0" class="pager" layout="total, prev, pager, next" :total="empTotal"
            :page-size="empPageSize" :current-page="empPage" @current-change="onEmpPage" />
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑部门' : '新建部门'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="部门名称" prop="departmentName">
          <el-input v-model="form.departmentName" />
        </el-form-item>
        <el-form-item label="部门编码" prop="departmentCode">
          <el-input v-model="form.departmentCode" />
        </el-form-item>
        <el-form-item label="父部门">
          <el-tree-select v-model="form.parentId" :data="parentOptions" check-strictly clearable
            :props="{ label: 'departmentName', children: 'children' }" node-key="id" :render-after-expand="false"
            placeholder="不选则为顶级部门" style="width: 100%" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="form.managerEmployeeId" clearable filterable placeholder="请选择员工" style="width: 100%">
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  changeDepartmentStatus,
  createDepartment,
  fetchDepartmentEmployees,
  fetchDepartmentTree,
  updateDepartment
} from '@/api/department'
import { useEmployeeOptions } from '@/composables/useEmployeeOptions'
import type { DepartmentVO, EmployeeListItem } from '@/api/types'
import { dictLabel, dictTagType } from '@/utils/dict'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const treeData = ref<DepartmentVO[]>([])
const treeLoading = ref(false)
const treeProps = { label: 'departmentName', children: 'children' }
const selected = ref<DepartmentVO | null>(null)

const employees = ref<EmployeeListItem[]>([])
const empLoading = ref(false)
const empPage = ref(1)
const empPageSize = 10
const empTotal = ref(0)
const empStatus = ref('PROBATION,REGULAR,TRANSFERRED')

const dialogVisible = ref(false)
const editing = ref<DepartmentVO | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  departmentName: '',
  departmentCode: '',
  parentId: null as number | null,
  managerEmployeeId: null as number | null,
  description: ''
})
const formRules: FormRules = {
  departmentName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  departmentCode: [{ required: true, message: '请输入部门编码', trigger: 'blur' }]
}

const { options: employeeOptions, load: loadEmployeeOptions } = useEmployeeOptions()

const parentOptions = computed(() => {
  const filter = (nodes: DepartmentVO[]): DepartmentVO[] =>
    nodes
      .filter((n) => n.id !== editing.value?.id)
      .map((n) => ({ ...n, children: n.children ? filter(n.children) : null }))
  return filter(treeData.value)
})

async function loadTree() {
  treeLoading.value = true
  try {
    treeData.value = await fetchDepartmentTree()
  } finally {
    treeLoading.value = false
  }
}

async function loadEmployees() {
  if (!selected.value) return
  empLoading.value = true
  try {
    const res = await fetchDepartmentEmployees(selected.value.id, empPage.value, empPageSize, empStatus.value)
    employees.value = res.records
    empTotal.value = res.total
  } finally {
    empLoading.value = false
  }
}

function onNodeClick(data: DepartmentVO) {
  selected.value = data
  empPage.value = 1
  loadEmployees()
}

function onStatusChange() {
  empPage.value = 1
  loadEmployees()
}

function onEmpPage(p: number) {
  empPage.value = p
  loadEmployees()
}

async function openDialog(dept?: DepartmentVO) {
  editing.value = dept || null
  await loadEmployeeOptions()
  if (dept) {
    form.departmentName = dept.departmentName
    form.departmentCode = dept.departmentCode
    form.parentId = dept.parentId
    form.managerEmployeeId = dept.managerEmployeeId
    form.description = dept.description || ''
  } else {
    form.departmentName = ''
    form.departmentCode = ''
    form.parentId = selected.value?.id ?? null
    form.managerEmployeeId = null
    form.description = ''
  }
  dialogVisible.value = true
}

async function onSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const body = {
      departmentName: form.departmentName,
      departmentCode: form.departmentCode,
      parentId: form.parentId,
      managerEmployeeId: form.managerEmployeeId,
      description: form.description || null
    }
    if (editing.value) {
      await updateDepartment(editing.value.id, body)
      ElMessage.success('部门已更新')
    } else {
      await createDepartment(body)
      ElMessage.success('部门已创建')
    }
    dialogVisible.value = false
    await loadTree()
    if (editing.value && selected.value?.id === editing.value.id) {
      selected.value = null
    }
  } finally {
    saving.value = false
  }
}

async function toggleStatus() {
  if (!selected.value) return
  const target = selected.value.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  await ElMessageBox.confirm(
    `确定${target === 'DISABLED' ? '停用' : '启用'}部门「${selected.value.departmentName}」吗？`,
    '提示',
    { type: 'warning' }
  )
  await changeDepartmentStatus(selected.value.id, target)
  ElMessage.success('操作成功')
  await loadTree()
  selected.value = null
  employees.value = []
}

onMounted(loadTree)
</script>

<style scoped>
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
