<template>
  <div v-loading="loading">
    <el-card v-if="detail" shadow="never" class="page-card">
      <template #header>
        <div class="detail-header">
          <span>{{ detail.employeeName }}（{{ detail.employeeNo }}）</span>
          <el-tag :type="dictTagType('employeeStatus', detail.employmentStatus)">
            {{ dictLabel('employeeStatus', detail.employmentStatus) }}
          </el-tag>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="性别">{{ dictLabel('gender', detail.gender) }}</el-descriptions-item>
        <el-descriptions-item label="出生日期">{{ detail.birthDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detail.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="部门">{{ detail.departmentName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="岗位">{{ detail.positionName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="职位层级">{{ dictLabelOf(levelItems, detail.positionLevelCode) }}</el-descriptions-item>
        <el-descriptions-item label="直属上级">{{ detail.managerEmployeeName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="学历">{{ dictLabelOf(eduItems, detail.educationCode) }}</el-descriptions-item>
        <el-descriptions-item label="入职日期">{{ detail.hireDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="离职日期">{{ detail.resignationDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="地址">{{ detail.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="紧急联系人">{{ detail.emergencyContactName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系人电话">{{ detail.emergencyContactPhone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="tab" class="tabs">
        <el-tab-pane label="任职历史" name="records">
          <el-table v-loading="recordsLoading" :data="records">
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ dictLabel('changeType', row.changeType) }}</template>
            </el-table-column>
            <el-table-column prop="effectiveDate" label="生效日期" width="120" />
            <el-table-column label="原部门">
              <template #default="{ row }">{{ row.oldDepartmentName || '-' }}</template>
            </el-table-column>
            <el-table-column label="新部门">
              <template #default="{ row }">{{ row.newDepartmentName || '-' }}</template>
            </el-table-column>
            <el-table-column label="原岗位">
              <template #default="{ row }">{{ row.oldPositionName || '-' }}</template>
            </el-table-column>
            <el-table-column label="新岗位">
              <template #default="{ row }">{{ row.newPositionName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" show-overflow-tooltip />
            <template #empty>
              <el-empty description="暂无任职历史" />
            </template>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="相关申请" name="requests">
          <el-table v-loading="requestsLoading" :data="requests">
            <el-table-column prop="requestNo" label="单号" width="180" />
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
            <el-table-column prop="createdAt" label="创建时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无申请" />
            </template>
          </el-table>
          <el-pagination v-if="reqTotal > 0" class="pager" layout="total, prev, pager, next" :total="reqTotal"
            :page-size="reqPageSize" :current-page="reqPage" @current-change="onReqPage" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  fetchEmployeeChangeRequests,
  fetchEmployeeRecords,
  getEmployee
} from '@/api/employee'
import { listDictItems } from '@/api/dictionary'
import type { ChangeRequestVO, DictionaryItem, EmployeeDetail, EmploymentRecordVO } from '@/api/types'
import { dictLabel, dictTagType } from '@/utils/dict'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()
const id = Number(route.params.id)

const loading = ref(false)
const detail = ref<EmployeeDetail | null>(null)
const tab = ref('records')
const records = ref<EmploymentRecordVO[]>([])
const recordsLoading = ref(false)
const requests = ref<ChangeRequestVO[]>([])
const requestsLoading = ref(false)
const reqPage = ref(1)
const reqPageSize = 10
const reqTotal = ref(0)
const eduItems = ref<DictionaryItem[]>([])
const levelItems = ref<DictionaryItem[]>([])

function dictLabelOf(items: DictionaryItem[], code: string | null): string {
  if (!code) return '-'
  return items.find((i) => i.itemCode === code)?.itemLabel || code
}

function formatTime(t: string | null): string {
  return t ? t.replace('T', ' ').slice(0, 19) : '-'
}

async function loadRequests() {
  requestsLoading.value = true
  try {
    const res = await fetchEmployeeChangeRequests(id, reqPage.value, reqPageSize)
    requests.value = res.records
    reqTotal.value = res.total
  } finally {
    requestsLoading.value = false
  }
}

function onReqPage(p: number) {
  reqPage.value = p
  loadRequests()
}

onMounted(async () => {
  loading.value = true
  try {
    detail.value = await getEmployee(id)
  } finally {
    loading.value = false
  }
  recordsLoading.value = true
  try {
    records.value = await fetchEmployeeRecords(id)
  } finally {
    recordsLoading.value = false
  }
  loadRequests()
  if (auth.has('dictionary:read')) {
    eduItems.value = await listDictItems('EDUCATION')
    levelItems.value = await listDictItems('POSITION_LEVEL')
  }
})
</script>

<style scoped>
.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 600;
}

.tabs {
  margin-top: 16px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
