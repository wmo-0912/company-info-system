<template>
  <div class="page-card">
    <div class="page-toolbar">
      <el-input v-model="keyword" placeholder="搜索姓名/编号" clearable style="width: 220px" @keyup.enter="reload"
        @clear="reload" />
      <el-tree-select v-if="auth.has('department:read')" v-model="filterDept" clearable filterable
        :data="deptTree" check-strictly node-key="id" :render-after-expand="false"
        :props="{ label: 'departmentName', children: 'children' }" placeholder="部门筛选" style="width: 180px"
        @change="reload" />
      <el-select v-model="status" placeholder="状态筛选" clearable style="width: 140px" @change="reload">
        <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-button type="primary" @click="reload">查询</el-button>
      <div class="spacer" />
      <el-button v-if="auth.has('employee:create')" type="primary" @click="openDialog()">新建员工</el-button>
    </div>

    <el-table v-loading="loading" :data="records">
      <el-table-column prop="employeeNo" label="编号" width="100" />
      <el-table-column prop="employeeName" label="姓名" />
      <el-table-column prop="departmentName" label="部门" />
      <el-table-column prop="positionName" label="岗位" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="dictTagType('employeeStatus', row.employmentStatus)">
            {{ dictLabel('employeeStatus', row.employmentStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="hireDate" label="入职日期" width="120" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/employees/${row.id}`)">详情</el-button>
          <el-button v-if="auth.has('employee:update')" link type="primary" @click="openDialog(row)">编辑</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无员工" />
      </template>
    </el-table>
    <el-pagination v-if="total > 0" class="pager" layout="total, sizes, prev, pager, next" :total="total"
      v-model:page-size="pageSize" v-model:current-page="page" :page-sizes="[10, 20, 50]" @change="load" />

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑员工' : '新建员工'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item v-if="!editing" label="员工编号" prop="employeeNo">
              <el-input v-model="form.employeeNo" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="employeeName">
              <el-input v-model="form.employeeName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别">
              <el-select v-model="form.gender" clearable style="width: 100%">
                <el-option label="男" value="MALE" />
                <el-option label="女" value="FEMALE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出生日期">
              <el-date-picker v-model="form.birthDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号">
              <el-input v-model="form.phone" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="form.email" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门" prop="departmentId">
              <el-tree-select v-model="form.departmentId" :data="deptTree" check-strictly node-key="id"
                :props="{ label: 'departmentName', children: 'children' }" :render-after-expand="false"
                :disabled="lockOrgFields" style="width: 100%" />
              <div v-if="lockOrgFields" class="form-hint">部门调整请走「入转调离 → 调岗」流程</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="岗位">
              <el-input v-model="form.positionName" :disabled="lockOrgFields" />
              <div v-if="lockOrgFields" class="form-hint">岗位调整请走「入转调离 → 调岗」流程</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="职位层级">
              <el-select v-model="form.positionLevelCode" clearable style="width: 100%">
                <el-option v-for="i in levelItems" :key="i.itemCode" :label="i.itemLabel" :value="i.itemCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="学历">
              <el-select v-model="form.educationCode" clearable style="width: 100%">
                <el-option v-for="i in eduItems" :key="i.itemCode" :label="i.itemLabel" :value="i.itemCode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="直属上级">
              <el-select v-model="form.managerEmployeeId" clearable filterable style="width: 100%">
                <el-option v-for="e in records" :key="e.id" :label="`${e.employeeName}（${e.employeeNo}）`"
                  :value="e.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入职日期">
              <el-date-picker v-model="form.hireDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地址">
              <el-input v-model="form.address" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="紧急联系人">
              <el-input v-model="form.emergencyContactName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人电话">
              <el-input v-model="form.emergencyContactPhone" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
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
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createEmployee, getEmployee, pageEmployees, updateEmployee } from '@/api/employee'
import { fetchDepartmentTree } from '@/api/department'
import { listDictItems } from '@/api/dictionary'
import type { DepartmentVO, DictionaryItem, EmployeeListItem } from '@/api/types'
import { dictLabel, dictOptions, dictTagType } from '@/utils/dict'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const keyword = ref('')
const status = ref('')
const filterDept = ref<number | null>(null)
const statusOptions = dictOptions('employeeStatus')
const records = ref<EmployeeListItem[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const editing = ref<EmployeeListItem | null>(null)
// 入职后部门/岗位只能由调岗流程变更，档案编辑只允许待入职员工修正
const lockOrgFields = computed(() => !!editing.value && editing.value.employmentStatus !== 'PENDING')
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  employeeNo: '',
  employeeName: '',
  gender: null as string | null,
  birthDate: null as string | null,
  phone: '',
  email: '',
  address: '',
  emergencyContactName: '',
  emergencyContactPhone: '',
  educationCode: null as string | null,
  departmentId: null as number | null,
  positionName: '',
  positionLevelCode: null as string | null,
  managerEmployeeId: null as number | null,
  hireDate: null as string | null,
  remark: ''
})
const formRules: FormRules = {
  employeeNo: [{ required: true, message: '请输入员工编号', trigger: 'blur' }],
  employeeName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  departmentId: [{ required: true, message: '请选择部门', trigger: 'change' }]
}

const deptTree = ref<DepartmentVO[]>([])
const eduItems = ref<DictionaryItem[]>([])
const levelItems = ref<DictionaryItem[]>([])

async function load() {
  loading.value = true
  try {
    const res = await pageEmployees({
      page: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
      departmentId: filterDept.value || undefined,
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

async function openDialog(row?: EmployeeListItem) {
  editing.value = row || null
  if (row) {
    const detail = await getEmployee(row.id)
    Object.assign(form, {
      employeeNo: detail.employeeNo,
      employeeName: detail.employeeName,
      gender: detail.gender,
      birthDate: detail.birthDate,
      phone: detail.phone || '',
      email: detail.email || '',
      address: detail.address || '',
      emergencyContactName: detail.emergencyContactName || '',
      emergencyContactPhone: detail.emergencyContactPhone || '',
      educationCode: detail.educationCode,
      departmentId: detail.departmentId,
      positionName: detail.positionName || '',
      positionLevelCode: detail.positionLevelCode,
      managerEmployeeId: detail.managerEmployeeId,
      hireDate: detail.hireDate,
      remark: detail.remark || ''
    })
  } else {
    Object.assign(form, {
      employeeNo: '',
      employeeName: '',
      gender: null,
      birthDate: null,
      phone: '',
      email: '',
      address: '',
      emergencyContactName: '',
      emergencyContactPhone: '',
      educationCode: null,
      departmentId: null,
      positionName: '',
      positionLevelCode: null,
      managerEmployeeId: null,
      hireDate: null,
      remark: ''
    })
  }
  dialogVisible.value = true
}

async function onSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const common = {
      employeeName: form.employeeName,
      gender: form.gender,
      birthDate: form.birthDate,
      phone: form.phone || null,
      email: form.email || null,
      address: form.address || null,
      emergencyContactName: form.emergencyContactName || null,
      emergencyContactPhone: form.emergencyContactPhone || null,
      educationCode: form.educationCode,
      departmentId: form.departmentId!,
      positionName: form.positionName || null,
      positionLevelCode: form.positionLevelCode,
      managerEmployeeId: form.managerEmployeeId,
      hireDate: form.hireDate,
      remark: form.remark || null
    }
    if (editing.value) {
      await updateEmployee(editing.value.id, common)
      ElMessage.success('员工已更新')
    } else {
      await createEmployee({ employeeNo: form.employeeNo, ...common })
      ElMessage.success('员工已创建')
    }
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  load()
  if (auth.has('department:read')) {
    deptTree.value = await fetchDepartmentTree()
  }
  if (auth.has('dictionary:read')) {
    eduItems.value = await listDictItems('EDUCATION')
    levelItems.value = await listDictItems('POSITION_LEVEL')
  }
})
</script>

<style scoped>
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.form-hint {
  margin-top: 2px;
  font-size: 12px;
  line-height: 16px;
  color: var(--el-text-color-secondary);
}
</style>
