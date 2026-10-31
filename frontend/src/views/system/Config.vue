<template>
  <div class="page-card">
    <el-alert type="info" :closable="false" show-icon class="tip"
      title="company.name 与 company.logo.url 会作为登录页与侧栏的公司名称 / Logo 展示，保存后立即生效。" />
    <el-table v-loading="loading" :data="records">
      <el-table-column prop="configKey" label="配置键" width="220" />
      <el-table-column prop="configValue" label="配置值">
        <template #default="{ row }">{{ row.configValue || '-' }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column v-if="auth.has('system:config:update')" label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无配置" />
      </template>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="`编辑配置 - ${current?.configKey || ''}`" width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="配置值">
          <el-input v-model="form.configValue" type="textarea" :rows="2" />
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listConfigs, upsertConfig } from '@/api/system'
import type { SysConfig } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useCompanyStore } from '@/stores/company'

const auth = useAuthStore()
const company = useCompanyStore()
const records = ref<SysConfig[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const current = ref<SysConfig | null>(null)
const form = reactive({ configValue: '', description: '' })

function formatTime(t: string | null): string {
  return t ? t.replace('T', ' ').slice(0, 19) : '-'
}

async function load() {
  loading.value = true
  try {
    records.value = await listConfigs()
  } finally {
    loading.value = false
  }
}

function openDialog(row: SysConfig) {
  current.value = row
  form.configValue = row.configValue || ''
  form.description = row.description || ''
  dialogVisible.value = true
}

async function onSave() {
  saving.value = true
  try {
    await upsertConfig(current.value!.configKey, form.configValue || null, form.description || null)
    ElMessage.success('配置已保存')
    dialogVisible.value = false
    // 公司名称 / Logo 同步到全局 store，登录页与侧栏无需刷新即可更新
    if (current.value?.configKey.startsWith('company.')) {
      await company.load(true)
    }
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.tip {
  margin-bottom: 12px;
}
</style>
