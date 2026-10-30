<template>
  <div v-loading="loading">
    <el-card v-if="profile" shadow="never" class="page-card">
      <template #header>个人信息</template>
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

      <el-tabs v-model="tab" class="tabs">
        <el-tab-pane label="资料编辑" name="edit">
          <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px" style="max-width: 560px">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" />
            </el-form-item>
            <el-form-item label="地址" prop="address">
              <el-input v-model="form.address" />
            </el-form-item>
            <el-form-item label="紧急联系人" prop="emergencyContactName">
              <el-input v-model="form.emergencyContactName" />
            </el-form-item>
            <el-form-item label="联系人电话" prop="emergencyContactPhone">
              <el-input v-model="form.emergencyContactPhone" />
            </el-form-item>
            <el-form-item label="学历" prop="educationCode">
              <el-select v-model="form.educationCode" clearable style="width: 100%">
                <el-option v-for="i in eduItems" :key="i.itemCode" :label="i.itemLabel" :value="i.itemCode" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button v-if="auth.has('profile:update')" type="primary" :loading="saving" @click="onSave">
                保存
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="我的任职历史" name="records">
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
      </el-tabs>
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
const profile = ref<EmployeeDetail | null>(null)
const tab = ref('edit')
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
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
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
.tabs {
  margin-top: 16px;
}
</style>
