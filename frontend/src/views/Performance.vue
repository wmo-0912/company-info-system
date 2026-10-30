<template>
  <div class="page-card">
    <el-tabs v-model="tab">
      <el-tab-pane label="绩效列表" name="list" />
      <el-tab-pane label="我的绩效" name="my" />
      <el-tab-pane label="我的申诉" name="appeals" />
      <el-tab-pane v-if="auth.has('performance:review')" label="申诉处理" name="review" />
    </el-tabs>

    <template v-if="tab === 'list'">
      <div class="page-toolbar">
        <el-select v-model="filterPeriodType" placeholder="周期类型" clearable style="width: 130px" @change="reload">
          <el-option v-for="o in periodTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-input v-model="filterPeriodKey" placeholder="周期（如 2026-09 / 2026-Q3）" clearable style="width: 200px"
          @keyup.enter="reload" @clear="reload" />
        <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 130px" @change="reload">
          <el-option v-for="o in reviewStatusOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button type="primary" @click="reload">查询</el-button>
        <div class="spacer" />
        <el-tag v-for="p in lockedPeriods" :key="p.periodKey" type="warning" class="lock-tag">
          {{ p.periodKey }} 已锁定
        </el-tag>
        <el-button v-if="auth.has('performance:review')" @click="onToggleLock(true)">锁定期</el-button>
        <el-button v-if="auth.has('performance:review')" @click="onToggleLock(false)">解锁期</el-button>
        <el-button v-if="auth.has('performance:review')" @click="openSummary">汇总</el-button>
        <el-button v-if="auth.has('performance:create')" type="primary" @click="openDialog()">新建评价</el-button>
      </div>
      <el-table v-loading="loading" :data="records">
        <el-table-column label="员工" width="150">
          <template #default="{ row }">{{ row.employeeName }}（{{ row.employeeNo }}）</template>
        </el-table-column>
        <el-table-column label="周期类型" width="100">
          <template #default="{ row }">{{ dictLabel('periodType', row.periodType) }}</template>
        </el-table-column>
        <el-table-column prop="periodKey" label="周期" width="110" />
        <el-table-column label="评分" width="90">
          <template #default="{ row }">{{ row.score ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="rating" label="评级" width="80">
          <template #default="{ row }">{{ row.rating || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="dictTagType('reviewStatus', row.status)">
              {{ dictLabel('reviewStatus', row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="comment" label="评语" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT' && auth.has('performance:update')" link type="primary"
              @click="openDialog(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT' && auth.has('performance:submit')" link type="success"
              @click="onSubmitReview(row)">提交</el-button>
            <el-button v-if="row.status === 'SUBMITTED' && auth.has('performance:review')" link type="warning"
              @click="onReturnReview(row)">退回</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无绩效记录" />
        </template>
      </el-table>
      <el-pagination v-if="total > 0" class="pager" layout="total, sizes, prev, pager, next" :total="total"
        v-model:page-size="pageSize" v-model:current-page="page" :page-sizes="[10, 20, 50]" @change="load" />
    </template>

    <template v-else>
      <el-table v-loading="myLoading" :data="myRecords">
        <el-table-column label="周期类型" width="100">
          <template #default="{ row }">{{ dictLabel('periodType', row.periodType) }}</template>
        </el-table-column>
        <el-table-column prop="periodKey" label="周期" width="110" />
        <el-table-column label="评分" width="90">
          <template #default="{ row }">{{ row.score ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="rating" label="评级" width="80">
          <template #default="{ row }">{{ row.rating || '-' }}</template>
        </el-table-column>
        <el-table-column prop="reviewerName" label="评价人" width="120" />
        <el-table-column prop="projectTeamName" label="项目组" width="160">
          <template #default="{ row }">{{ row.projectTeamName || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="dictTagType('reviewStatus', row.status)">
              {{ dictLabel('reviewStatus', row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="comment" label="评语" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'SUBMITTED'" link type="warning" @click="onAppeal(row)">申诉</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无我的绩效" />
        </template>
      </el-table>
      <el-pagination v-if="myTotal > 0" class="pager" layout="total, prev, pager, next" :total="myTotal"
        :page-size="myPageSize" :current-page="myPage" @current-change="onMyPage" />
    </template>

    <template v-else-if="tab === 'appeals'">
      <el-table v-loading="appealLoading" :data="myAppeals">
        <el-table-column prop="periodKey" label="周期" width="110" />
        <el-table-column label="评分" width="80">
          <template #default="{ row }">{{ row.score ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="申诉理由" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="appealTagType(row.status)">{{ appealLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handleComment" label="处理意见" show-overflow-tooltip>
          <template #default="{ row }">{{ row.handleComment || '-' }}</template>
        </el-table-column>
        <el-table-column prop="handlerName" label="处理人" width="110">
          <template #default="{ row }">{{ row.handlerName || '-' }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无申诉" />
        </template>
      </el-table>
      <el-pagination v-if="appealTotal > 0" class="pager" layout="total, prev, pager, next" :total="appealTotal"
        :page-size="10" :current-page="appealPage" @current-change="onAppealPage" />
    </template>

    <template v-else>
      <div class="page-toolbar">
        <el-select v-model="appealFilterStatus" placeholder="状态" clearable style="width: 140px" @change="loadPendingAppeals">
          <el-option label="待处理" value="PENDING" />
          <el-option label="已受理" value="ACCEPTED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-button @click="loadPendingAppeals">刷新</el-button>
      </div>
      <el-table v-loading="pendingLoading" :data="pendingAppeals">
        <el-table-column label="员工" width="130">
          <template #default="{ row }">{{ row.employeeName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="periodKey" label="周期" width="110" />
        <el-table-column label="评分" width="80">
          <template #default="{ row }">{{ row.score ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="申诉理由" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="appealTagType(row.status)">{{ appealLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handleComment" label="处理意见" show-overflow-tooltip>
          <template #default="{ row }">{{ row.handleComment || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button link type="primary" @click="onHandleAppeal(row, 'ACCEPT')">受理</el-button>
              <el-button link type="danger" @click="onHandleAppeal(row, 'REJECT')">驳回</el-button>
            </template>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无申诉" />
        </template>
      </el-table>
      <el-pagination v-if="pendingTotal > 0" class="pager" layout="total, prev, pager, next" :total="pendingTotal"
        :page-size="10" :current-page="pendingPage" @current-change="onPendingPage" />
    </template>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑评价' : '新建评价'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="被评价人" prop="employeeId">
          <el-select v-model="form.employeeId" filterable style="width: 100%" :disabled="!!editing">
            <el-option v-for="e in employeeOptions" :key="e.id" :label="`${e.employeeName}（${e.employeeNo}）`"
              :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="周期类型" prop="periodType">
          <el-select v-model="form.periodType" style="width: 100%">
            <el-option v-for="o in periodTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="周期" prop="periodKey">
          <el-input v-model="form.periodKey" placeholder="YYYY-MM 或 YYYY-Qn" />
        </el-form-item>
        <el-form-item label="评分">
          <el-input-number v-model="form.score" :min="0" :max="100" :precision="1" style="width: 180px" />
        </el-form-item>
        <el-form-item label="评级">
          <el-input v-model="form.rating" placeholder="如 A / B+（可选）" style="width: 180px" />
        </el-form-item>
        <el-form-item label="评语">
          <el-input v-model="form.comment" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="summaryVisible" title="绩效汇总" width="720px" destroy-on-close>
      <div v-loading="summaryLoading">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="周期类型">
            {{ filterPeriodType ? dictLabel('periodType', filterPeriodType) : '全部' }}
          </el-descriptions-item>
          <el-descriptions-item label="周期">{{ filterPeriodKey || '全部' }}</el-descriptions-item>
          <el-descriptions-item label="评价条数">{{ summary?.reviewCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="被评价人数">{{ summary?.employeeCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="平均分">{{ summary?.averageScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="最高 / 最低">
            {{ summary?.maxScore ?? '-' }} / {{ summary?.minScore ?? '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <h4 class="summary-title">分数段分布</h4>
        <el-table :data="summary?.scoreBuckets ?? []" size="small">
          <el-table-column prop="label" label="分数段" width="120" />
          <el-table-column prop="count" label="评价条数" width="110" />
          <el-table-column label="占比">
            <template #default="{ row }">
              <el-progress :percentage="percent(row.count)" />
            </template>
          </el-table-column>
        </el-table>

        <h4 class="summary-title">按部门</h4>
        <el-table :data="summary?.departments ?? []" size="small">
          <el-table-column prop="name" label="部门" />
          <el-table-column prop="employeeCount" label="人数" width="90" />
          <el-table-column prop="reviewCount" label="评价数" width="100" />
          <el-table-column label="平均分" width="100">
            <template #default="{ row }">{{ row.averageScore ?? '-' }}</template>
          </el-table-column>
        </el-table>

        <h4 class="summary-title">按项目组</h4>
        <el-table :data="summary?.projects ?? []" size="small">
          <el-table-column prop="name" label="项目组" />
          <el-table-column prop="employeeCount" label="人数" width="90" />
          <el-table-column prop="reviewCount" label="评价数" width="100" />
          <el-table-column label="平均分" width="100">
            <template #default="{ row }">{{ row.averageScore ?? '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="summaryVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createAppeal,
  createReview,
  fetchAppeals,
  fetchMyAppeals,
  fetchPerformanceSummary,
  handleAppeal,
  fetchMyReviews,
  fetchPeriods,
  pageReviews,
  returnReview,
  setPeriodLocked,
  submitReview,
  updateReview,
  type AppealStatus,
  type PerformanceAppealVO,
  type PerformancePeriodVO,
  type PerformanceSummary
} from '@/api/performance'
import { useEmployeeOptions } from '@/composables/useEmployeeOptions'
import type { PerformanceReviewVO } from '@/api/types'
import { dictLabel, dictOptions, dictTagType } from '@/utils/dict'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const tab = ref<'list' | 'my' | 'appeals' | 'review'>('list')

const periodTypeOptions = dictOptions('periodType')
const reviewStatusOptions = dictOptions('reviewStatus')
const filterPeriodType = ref('')
const filterPeriodKey = ref('')
const filterStatus = ref('')
const lockedPeriods = ref<PerformancePeriodVO[]>([])
const records = ref<PerformanceReviewVO[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const myRecords = ref<PerformanceReviewVO[]>([])
const myLoading = ref(false)
const myPage = ref(1)
const myPageSize = 10
const myTotal = ref(0)
const myLoaded = ref(false)

const myAppeals = ref<PerformanceAppealVO[]>([])
const appealLoading = ref(false)
const appealPage = ref(1)
const appealTotal = ref(0)
const pendingAppeals = ref<PerformanceAppealVO[]>([])
const pendingLoading = ref(false)
const pendingPage = ref(1)
const pendingTotal = ref(0)
const appealFilterStatus = ref('PENDING')

const APPEAL_LABELS: Record<AppealStatus, string> = {
  PENDING: '待处理',
  ACCEPTED: '已受理',
  REJECTED: '已驳回'
}
const appealLabel = (status: AppealStatus) => APPEAL_LABELS[status] || status
const appealTagType = (status: AppealStatus) =>
  status === 'PENDING' ? 'warning' : status === 'ACCEPTED' ? 'success' : 'danger'

const dialogVisible = ref(false)
const editing = ref<PerformanceReviewVO | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  employeeId: null as number | null,
  periodType: 'MONTHLY',
  periodKey: '',
  score: null as number | null,
  rating: '',
  comment: ''
})
const periodKeyPattern = /^\d{4}-(0[1-9]|1[0-2]|Q[1-4])$/
const formRules: FormRules = {
  employeeId: [{ required: true, message: '请选择被评价人', trigger: 'change' }],
  periodType: [{ required: true, message: '请选择周期类型', trigger: 'change' }],
  periodKey: [
    { required: true, message: '请输入周期', trigger: 'blur' },
    { pattern: periodKeyPattern, message: '格式应为 YYYY-MM 或 YYYY-Qn', trigger: 'blur' }
  ]
}

const { options: employeeOptions, load: loadEmployeeOptions } = useEmployeeOptions()

const summaryVisible = ref(false)
const summaryLoading = ref(false)
const summary = ref<PerformanceSummary | null>(null)

/** 按当前筛选的周期打开汇总弹窗 */
async function openSummary() {
  summaryVisible.value = true
  summaryLoading.value = true
  try {
    summary.value = await fetchPerformanceSummary(
      filterPeriodType.value || undefined,
      filterPeriodKey.value.trim() || undefined
    )
  } finally {
    summaryLoading.value = false
  }
}

/** 分数段占比：分母用评价条数 */
function percent(count: number) {
  const total = summary.value?.reviewCount ?? 0
  return total > 0 ? Math.round((count / total) * 100) : 0
}

async function loadPeriods() {
  lockedPeriods.value = (await fetchPeriods()).filter((p) => p.locked)
}

/** 锁定/解锁周期：周期标识取筛选条件，未填则弹窗询问 */
async function onToggleLock(locked: boolean) {
  let periodKey = filterPeriodKey.value.trim()
  if (!periodKey) {
    const input = await ElMessageBox.prompt('请输入周期标识，例如 2026-Q3', locked ? '锁定期' : '解锁期', {
      inputPattern: /^\d{4}-(0[1-9]|1[0-2]|Q[1-4])$/,
      inputErrorMessage: '格式应为 YYYY-MM 或 YYYY-Qn'
    })
    periodKey = input.value.trim()
  }
  await ElMessageBox.confirm(
    locked
      ? `锁定后周期 ${periodKey} 内不能再新增、修改或提交评价，确定锁定？`
      : `确定解除周期 ${periodKey} 的锁定？`,
    locked ? '锁定期' : '解锁期',
    { type: 'warning' }
  )
  await setPeriodLocked(periodKey, locked)
  ElMessage.success(locked ? '已锁定该周期' : '已解除锁定')
  await loadPeriods()
}

async function load() {
  loading.value = true
  try {
    const res = await pageReviews({
      page: page.value,
      pageSize: pageSize.value,
      periodType: filterPeriodType.value || undefined,
      periodKey: filterPeriodKey.value || undefined,
      status: filterStatus.value || undefined
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

async function loadMyAppeals() {
  appealLoading.value = true
  try {
    const res = await fetchMyAppeals(appealPage.value, 10)
    myAppeals.value = res.records
    appealTotal.value = res.total
  } finally {
    appealLoading.value = false
  }
}

function onAppealPage(p: number) {
  appealPage.value = p
  loadMyAppeals()
}

async function loadPendingAppeals() {
  pendingLoading.value = true
  try {
    const res = await fetchAppeals(pendingPage.value, 10, appealFilterStatus.value || undefined)
    pendingAppeals.value = res.records
    pendingTotal.value = res.total
  } finally {
    pendingLoading.value = false
  }
}

function onPendingPage(p: number) {
  pendingPage.value = p
  loadPendingAppeals()
}

/** 员工对本人收到的评价提出申诉，理由必填 */
async function onAppeal(row: PerformanceReviewVO) {
  const { value } = await ElMessageBox.prompt('请说明申诉理由，人事会据此复核', '绩效申诉', {
    inputType: 'textarea',
    inputPlaceholder: '如：本季度承担了额外项目，评分未体现',
    inputValidator: (val) => (val && val.trim().length > 0) || '请填写申诉理由',
    inputValue: ''
  })
  await createAppeal(row.id, value.trim())
  ElMessage.success('申诉已提交，等待人事处理')
  loadMy()
}

/** 人事处理申诉：受理会把评价退回评价人复核，驳回必须写处理意见 */
async function onHandleAppeal(row: PerformanceAppealVO, decision: 'ACCEPT' | 'REJECT') {
  const { value } = await ElMessageBox.prompt(
    decision === 'ACCEPT'
      ? '受理后该评价会退回评价人重新评分，请填写处理意见'
      : '请填写驳回原因（会记入审计日志）',
    decision === 'ACCEPT' ? '受理申诉' : '驳回申诉',
    {
      inputType: 'textarea',
      inputValidator: (val) => {
        if (decision === 'REJECT' && (!val || !val.trim())) return '驳回必须填写处理意见'
        return true
      },
      inputValue: ''
    }
  )
  await handleAppeal(row.id, decision, value?.trim())
  ElMessage.success(decision === 'ACCEPT' ? '已受理，评价已退回评价人' : '已驳回')
  loadPendingAppeals()
}

async function loadMy() {
  myLoading.value = true
  try {
    const res = await fetchMyReviews(myPage.value, myPageSize)
    myRecords.value = res.records
    myTotal.value = res.total
    myLoaded.value = true
  } finally {
    myLoading.value = false
  }
}

function onMyPage(p: number) {
  myPage.value = p
  loadMy()
}

watch(tab, (t) => {
  if (t === 'my' && !myLoaded.value) loadMy()
  if (t === 'appeals') loadMyAppeals()
  if (t === 'review') loadPendingAppeals()
})

async function openDialog(row?: PerformanceReviewVO) {
  editing.value = row || null
  await loadEmployeeOptions()
  if (row) {
    Object.assign(form, {
      employeeId: row.employeeId,
      periodType: row.periodType,
      periodKey: row.periodKey,
      score: row.score,
      rating: row.rating || '',
      comment: row.comment || ''
    })
  } else {
    Object.assign(form, {
      employeeId: null,
      periodType: 'MONTHLY',
      periodKey: '',
      score: null,
      rating: '',
      comment: ''
    })
  }
  dialogVisible.value = true
}

async function onSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const body = {
      employeeId: form.employeeId!,
      periodType: form.periodType,
      periodKey: form.periodKey,
      score: form.score,
      rating: form.rating || null,
      comment: form.comment || null
    }
    if (editing.value) {
      await updateReview(editing.value.id, body)
      ElMessage.success('评价已更新')
    } else {
      await createReview(body)
      ElMessage.success('评价已创建（草稿）')
    }
    dialogVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function onReturnReview(row: PerformanceReviewVO) {
  const { value } = await ElMessageBox.prompt('请填写退回原因（会记入审计日志）', '退回评价', {
    type: 'warning',
    inputType: 'textarea',
    inputPlaceholder: '如：评分与考勤记录不符',
    inputValidator: (val) => (val && val.trim().length > 0) || '请填写退回原因',
    inputValue: ''
  })
  await returnReview(row.id, value.trim())
  ElMessage.success('已退回，评价人可修改后重新提交')
  load()
}

async function onSubmitReview(row: PerformanceReviewVO) {
  await ElMessageBox.confirm('确定提交该绩效评价吗？提交后不可编辑。', '提示', { type: 'warning' })
  await submitReview(row.id)
  ElMessage.success('已提交')
  load()
}

onMounted(() => {
  load()
  loadPeriods()
})
</script>

<style scoped>
.lock-tag {
  margin-right: 4px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.summary-title {
  margin: 16px 0 8px;
  font-size: 14px;
  color: #606266;
}
</style>
