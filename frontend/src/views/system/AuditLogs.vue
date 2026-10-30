<template>
  <div class="page-card">
    <div class="page-toolbar">
      <el-input v-model="actorName" placeholder="操作人" clearable style="width: 160px" @keyup.enter="reload"
        @clear="reload" />
      <el-input v-model="resourceType" placeholder="资源类型（如 employee）" clearable style="width: 180px"
        @keyup.enter="reload" @clear="reload" />
      <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期"
        end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 260px" />
      <el-button type="primary" @click="reload">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="records">
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="actorName" label="操作人" width="120">
        <template #default="{ row }">{{ row.actorName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="action" label="动作" width="180" show-overflow-tooltip />
      <el-table-column prop="resourceType" label="资源类型" width="110" />
      <el-table-column prop="resourceId" label="资源ID" width="100">
        <template #default="{ row }">{{ row.resourceId || '-' }}</template>
      </el-table-column>
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="dictTagType('auditResult', row.result)">
            {{ dictLabel('auditResult', row.result) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="summary" label="详情" show-overflow-tooltip />
      <template #empty>
        <el-empty description="暂无审计日志" />
      </template>
    </el-table>
    <el-pagination v-if="total > 0" class="pager" layout="total, sizes, prev, pager, next" :total="total"
      v-model:page-size="pageSize" v-model:current-page="page" :page-sizes="[10, 20, 50]" @change="load" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { pageAuditLogs } from '@/api/audit'
import type { AuditLog } from '@/api/types'
import { dictLabel, dictTagType } from '@/utils/dict'

const actorName = ref('')
const resourceType = ref('')
const dateRange = ref<[string, string] | null>(null)
const records = ref<AuditLog[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

function formatTime(t: string | null): string {
  return t ? t.replace('T', ' ').slice(0, 19) : '-'
}

async function load() {
  loading.value = true
  try {
    const res = await pageAuditLogs({
      page: page.value,
      pageSize: pageSize.value,
      actorName: actorName.value || undefined,
      resourceType: resourceType.value || undefined,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1]
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

onMounted(load)
</script>

<style scoped>
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
