<template>
  <div v-loading="loading">
    <el-row :gutter="16">
      <el-col v-for="card in cards" :key="card.label" :span="8" class="card-col">
        <el-card shadow="hover">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value">{{ card.value }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchDashboardSummary } from '@/api/system'
import type { DashboardSummary } from '@/api/types'

const loading = ref(false)
const summary = ref<DashboardSummary | null>(null)
const cards = ref<{ label: string; value: number }[]>([])

onMounted(async () => {
  loading.value = true
  try {
    summary.value = await fetchDashboardSummary()
    cards.value = [
      { label: '在职员工数', value: summary.value.employeeCount },
      { label: '试用期人数', value: summary.value.probationCount },
      { label: '近30天入职', value: summary.value.recentHireCount },
      { label: '待审批申请', value: summary.value.pendingChangeCount },
      { label: '我负责的项目组', value: summary.value.myTeamCount },
      { label: '我的草稿绩效', value: summary.value.myDraftReviewCount }
    ]
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.card-col {
  margin-bottom: 16px;
}

.stat-label {
  color: #909399;
  font-size: 14px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  margin-top: 8px;
}
</style>
