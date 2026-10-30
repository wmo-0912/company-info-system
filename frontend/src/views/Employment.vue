<template>
  <div class="page-card">
    <div class="page-toolbar">
      <el-select v-model="filterStatus" placeholder="状态筛选" clearable style="width: 140px" @change="reload">
        <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-select v-model="filterType" placeholder="类型筛选" clearable style="width: 140px" @change="reload">
        <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <div class="spacer" />
      <el-button v-if="auth.has('employment:submit')" type="primary" @click="openCreate">新建申请</el-button>
    </div>

    <el-table v-loading="loading" :data="records">
      <el-table-column prop="requestNo" label="单号" width="180" />
      <el-table-column label="员工" width="140">
        <template #default="{ row }">{{ row.employeeName }}（{{ row.employeeNo }}）</template>
      </el-table-column>
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ dictLabel('changeType', row.changeType) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="dictTagType('requestStatus', row.status)">
            {{ dictLabel('requestStatus', row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="effectiveDate" label="生效日期" width="120" />
      <el-table-column prop="reason" label="原因" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'DRAFT' && auth.has('employment:submit')" link type="primary"
            @click="onSubmit(row)">提交</el-button>
          <el-button v-if="(row.status === 'DRAFT' || row.status === 'SUBMITTED') && auth.has('employment:submit')"
            link type="warning" @click="onCancel(row)">取消</el-button>
          <el-button v-if="row.status === 'SUBMITTED' && auth.has('employment:process')" link type="success"
            @click="openProcess(row)">办理</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无申请" />
      </template>
    </el-table>
    <el-pagination v-if="total > 0" class="pager" layout="total, sizes, prev, pager, next" :total="total"
      v-model:page-size="pageSize" v-model:current-page="page" :page-sizes="[10, 20, 50]" @change="load" />

    <el-dialog v-model="createVisible" title="新建申请" width="520px" destroy-on-close>
      <el-form ref="createRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="员工" prop="employeeId">
          <el-select v-model="createForm.employeeId" filterable style="width: 100%" placeholder="请选择员工">
            <el-option v-for="e in employeeOptions" :key="e.id" :label="`${e.employeeName}（${e.employeeNo}）`"
              :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" prop="changeType">
          <el-select v-model="createForm.changeType" style="width: 100%">
            <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <template v-if="createForm.changeType === 'TRANSFER'">
          <el-form-item label="目标部门" prop="targetDepartmentId">
            <el-tree-select v-model="createForm.targetDepartmentId" :data="deptTree" check-strictly node-key="id"
              :props="{ label: 'departmentName', children: 'children' }" :render-after-expand="false"
              style="width: 100%" />
          </el-form-item>
          <el-form-item label="目标岗位">
            <el-input v-model="createForm.targetPositionName" />
          </el-form-item>
        </template>
        <el-form-item label="生效日期" prop="effectiveDate">
          <el-date-picker v-model="createForm.effectiveDate" type="date" value-format="YYYY-MM-DD"
            style="width: 100%" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="createForm.reason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">保存草稿</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="processVisible" title="办理申请" width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="处理决定" required>
          <el-radio-group v-model="processForm.decision">
            <el-radio value="APPROVE">批准</el-radio>
            <el-radio value="REJECT">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理意见">
          <el-input v-model="processForm.comment" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <el-alert v-if="approveBlocked" type="warning" :closable="false" show-icon
        title="生效日期未到，当前不能批准"
        description="提前批准会让变更立即生效；请驳回归还申请人修改生效日期，或等到生效日期当天再办理。" />
      <template #footer>
        <el-button @click="processVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="approveBlocked" @click="onProcess">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  cancelChangeRequest,
  createChangeRequest,
  pageChangeRequests,
  processChangeRequest,
  submitChangeRequest
} from '@/api/employment'
import { pageEmployees } from '@/api/employee'
import { fetchDepartmentTree } from '@/api/department'
import type { ChangeRequestVO, DepartmentVO, EmployeeListItem } from '@/api/types'
import { dictLabel, dictOptions, dictTagType } from '@/utils/dict'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const filterStatus = ref('')
const filterType = ref('')
const statusOptions = dictOptions('requestStatus')
const typeOptions = dictOptions('changeType')
const records = ref<ChangeRequestVO[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const createVisible = ref(false)
const processVisible = ref(false)
const saving = ref(false)
const createRef = ref<FormInstance>()
const createForm = reactive({
  employeeId: null as number | null,
  changeType: 'ONBOARD',
  effectiveDate: null as string | null,
  reason: '',
  targetDepartmentId: null as number | null,
  targetPositionName: ''
})
const createRules: FormRules = {
  employeeId: [{ required: true, message: '请选择员工', trigger: 'change' }],
  changeType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  effectiveDate: [{ required: true, message: '请选择生效日期', trigger: 'change' }],
  targetDepartmentId: [{ required: true, message: '请选择目标部门', trigger: 'change' }]
}
const processForm = reactive({ id: 0, decision: 'APPROVE', comment: '', effectiveDate: '' })

// 生效日期未到不能批准：后端同样拦截，这里提前提示避免无谓报错
function todayStr() {
  const d = new Date()
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}
const futureEffective = computed(() => {
  if (!processForm.effectiveDate) return false
  return processForm.effectiveDate > todayStr()
})
const approveBlocked = computed(() => processForm.decision === 'APPROVE' && futureEffective.value)

const employeeOptions = ref<EmployeeListItem[]>([])
const deptTree = ref<DepartmentVO[]>([])

async function load() {
  loading.value = true
  try {
    const res = await pageChangeRequests({
      page: page.value,
      pageSize: pageSize.value,
      status: filterStatus.value || undefined,
      changeType: filterType.value || undefined
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

async function openCreate() {
  if (!employeeOptions.value.length) {
    const res = await pageEmployees({ page: 1, pageSize: 100 })
    employeeOptions.value = res.records
  }
  if (!deptTree.value.length && auth.has('department:read')) {
    deptTree.value = await fetchDepartmentTree()
  }
  Object.assign(createForm, {
    employeeId: null,
    changeType: 'ONBOARD',
    effectiveDate: null,
    reason: '',
    targetDepartmentId: null,
    targetPositionName: ''
  })
  createVisible.value = true
}

async function onCreate() {
  await createRef.value?.validate()
  saving.value = true
  try {
    const payload: Record<string, unknown> = {}
    if (createForm.changeType === 'TRANSFER') {
      payload.targetDepartmentId = createForm.targetDepartmentId
      if (createForm.targetPositionName) {
        payload.targetPositionName = createForm.targetPositionName
      }
    }
    await createChangeRequest({
      employeeId: createForm.employeeId!,
      changeType: createForm.changeType,
      effectiveDate: createForm.effectiveDate!,
      reason: createForm.reason || null,
      payload: Object.keys(payload).length ? payload : null
    })
    ElMessage.success('申请已创建（草稿）')
    createVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function onSubmit(row: ChangeRequestVO) {
  await ElMessageBox.confirm(`确定提交申请 ${row.requestNo} 吗？`, '提示', { type: 'warning' })
  await submitChangeRequest(row.id)
  ElMessage.success('已提交')
  load()
}

async function onCancel(row: ChangeRequestVO) {
  await ElMessageBox.confirm(`确定取消申请 ${row.requestNo} 吗？`, '提示', { type: 'warning' })
  await cancelChangeRequest(row.id)
  ElMessage.success('已取消')
  load()
}

function openProcess(row: ChangeRequestVO) {
  processForm.id = row.id
  processForm.decision = 'APPROVE'
  processForm.comment = ''
  processForm.effectiveDate = row.effectiveDate || ''
  processVisible.value = true
}

async function onProcess() {
  saving.value = true
  try {
    await processChangeRequest(processForm.id, processForm.decision, processForm.comment || undefined)
    ElMessage.success('办理完成')
    processVisible.value = false
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
