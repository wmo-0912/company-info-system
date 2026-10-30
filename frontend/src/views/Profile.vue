<template>
  <div v-loading="loading">
    <el-card v-if="profile" shadow="never" class="page-card">
      <template #header>
        <div class="card-header">
          <span>个人中心</span>
          <div v-if="auth.has('profile:update')">
            <el-button v-if="!editing" type="primary" size="small" @click="startEdit">更改</el-button>
            <template v-else>
              <el-button size="small" @click="cancelEdit">取消</el-button>
              <el-button type="primary" size="small" :loading="saving" @click="onSave">保存</el-button>
            </template>
          </div>
        </div>
      </template>

      <div class="section-title">基本信息</div>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="姓名">{{ profile.employeeName }}</el-descriptions-item>
        <el-descriptions-item label="工号">{{ profile.employeeNo }}</el-descriptions-item>
        <el-descriptions-item label="部门">{{ profile.departmentName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="岗位">{{ profile.positionName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="入职日期">{{ profile.hireDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="dictTagType('employeeStatus', profile.employmentStatus)">
            {{ dictLabel('employeeStatus', profile.employmentStatus) }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <div class="section-title">
        可修改资料
        <span v-if="!editing" class="readonly-hint">（只读，点击右上角“更改”后可编辑）</span>
      </div>
      <el-form ref="formRef" :model="form" :rules="formRules">
        <el-descriptions :column="2" border class="edit-table">
          <el-descriptions-item label="手机号" label-class-name="desc-label">
            <el-form-item prop="phone" class="cell-item">
              <span v-if="!editing">{{ form.phone || '-' }}</span>
              <el-input v-else v-model="form.phone" size="small" placeholder="请输入手机号" />
            </el-form-item>
          </el-descriptions-item>
          <el-descriptions-item label="邮箱" label-class-name="desc-label">
            <el-form-item prop="email" class="cell-item">
              <span v-if="!editing">{{ form.email || '-' }}</span>
              <el-input v-else v-model="form.email" size="small" placeholder="请输入邮箱" />
            </el-form-item>
          </el-descriptions-item>
          <el-descriptions-item label="地址" label-class-name="desc-label">
            <el-form-item prop="address" class="cell-item">
              <span v-if="!editing">{{ form.address || '-' }}</span>
              <el-input v-else v-model="form.address" size="small" placeholder="请输入地址" />
            </el-form-item>
          </el-descriptions-item>
          <el-descriptions-item label="学历" label-class-name="desc-label">
            <el-form-item prop="educationCode" class="cell-item">
              <span v-if="!editing">{{ eduLabel(form.educationCode) }}</span>
              <el-select v-else v-model="form.educationCode" size="small" clearable style="width: 100%">
                <el-option v-for="i in eduItems" :key="i.itemCode" :label="i.itemLabel" :value="i.itemCode" />
              </el-select>
            </el-form-item>
          </el-descriptions-item>
          <el-descriptions-item label="紧急联系人" label-class-name="desc-label">
            <el-form-item prop="emergencyContactName" class="cell-item">
              <span v-if="!editing">{{ form.emergencyContactName || '-' }}</span>
              <el-input v-else v-model="form.emergencyContactName" size="small" placeholder="请输入紧急联系人" />
            </el-form-item>
          </el-descriptions-item>
          <el-descriptions-item label="联系人电话" label-class-name="desc-label">
            <el-form-item prop="emergencyContactPhone" class="cell-item">
              <span v-if="!editing">{{ form.emergencyContactPhone || '-' }}</span>
              <el-input v-else v-model="form.emergencyContactPhone" size="small" placeholder="请输入联系人电话" />
            </el-form-item>
          </el-descriptions-item>
        </el-descriptions>
      </el-form>

      <div class="section-title">我的任职历史</div>
      <el-table v-loading="recordsLoading" :data="records" border>
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
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { fetchMyEmploymentRecords, fetchProfile, updateProfile } from '@/api/profile'
import { listDictItems } from '@/api/dictionary'
import type { DictionaryItem, EmployeeDetail, EmploymentRecordVO } from '@/api/types'
import { dictLabel, dictTagType } from '@/utils/dict'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const editing = ref(false)
const profile = ref<EmployeeDetail | null>(null)
const records = ref<EmploymentRecordVO[]>([])
const recordsLoading = ref(false)
const eduItems = ref<DictionaryItem[]>([])
const formRef = ref<FormInstance>()

const form = reactive({
  phone: '',
  email: '',
  address: '',
  emergencyContactName: '',
  emergencyContactPhone: '',
  educationCode: null as string | null
})
const formRules: FormRules = {
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }]
}

function fillForm(p: EmployeeDetail) {
  Object.assign(form, {
    phone: p.phone || '',
    email: p.email || '',
    address: p.address || '',
    emergencyContactName: p.emergencyContactName || '',
    emergencyContactPhone: p.emergencyContactPhone || '',
    educationCode: p.educationCode
  })
}

function eduLabel(code: string | null) {
  if (!code) return '-'
  return eduItems.value.find((i) => i.itemCode === code)?.itemLabel || code
}

function startEdit() {
  editing.value = true
}

function cancelEdit() {
  if (profile.value) fillForm(profile.value)
  editing.value = false
  formRef.value?.clearValidate()
}

async function onSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const updated = await updateProfile({
      phone: form.phone || null,
      email: form.email || null,
      address: form.address || null,
      emergencyContactName: form.emergencyContactName || null,
      emergencyContactPhone: form.emergencyContactPhone || null,
      educationCode: form.educationCode
    })
    profile.value = updated
    fillForm(updated)
    editing.value = false
    ElMessage.success('资料已保存')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    profile.value = await fetchProfile()
    fillForm(profile.value)
  } finally {
    loading.value = false
  }
  recordsLoading.value = true
  try {
    records.value = await fetchMyEmploymentRecords()
  } finally {
    recordsLoading.value = false
  }
  if (auth.has('dictionary:read')) {
    eduItems.value = await listDictItems('EDUCATION')
  }
})
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-title {
  margin: 20px 0 10px;
  font-weight: 600;
  color: #303133;
}

.section-title:first-of-type {
  margin-top: 0;
}

.readonly-hint {
  font-weight: 400;
  font-size: 12px;
  color: #909399;
}

.edit-table :deep(.desc-label) {
  width: 120px;
  background: #fafafa;
}

.cell-item {
  margin-bottom: 0;
}
</style>
