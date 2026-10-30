<template>
  <div class="page-card">
    <el-tabs v-model="tab">
      <el-tab-pane label="绩效列表" name="list" />
      <el-tab-pane label="我的绩效" name="my" />
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
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT' && auth.has('performance:update')" link type="primary"
              @click="openDialog(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT' && auth.has('performance:submit')" link type="success"
              @click="onSubmitReview(row)">提交</el-button>
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
        <template #empty>
          <el-empty description="暂无我的绩效" />
        </template>
      </el-table>
      <el-pagination v-if="myTotal > 0" class="pager" layout="total, prev, pager, next" :total="myTotal"
        :page-size="myPageSize" :current-page="myPage" @current-change="onMyPage" />
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createReview,
  fetchMyReviews,
  pageReviews,
  submitReview,
  updateReview
} from '@/api/performance'
import { useEmployeeOptions } from '@/composables/useEmployeeOptions'
import type { PerformanceReviewVO } from '@/api/types'
import { dictLabel, dictOptions, dictTagType } from '@/utils/dict'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const tab = ref<'list' | 'my'>('list')

const periodTypeOptions = dictOptions('periodType')
const reviewStatusOptions = dictOptions('reviewStatus')
const filterPeriodType = ref('')
const filterPeriodKey = ref('')
const filterStatus = ref('')
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

async function onSubmitReview(row: PerformanceReviewVO) {
  await ElMessageBox.confirm('确定提交该绩效评价吗？提交后不可编辑。', '提示', { type: 'warning' })
  await submitReview(row.id)
  ElMessage.success('已提交')
  load()
}

onMounted(load)
</script>

<style scoped>
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
