<template>
  <div class="page-card">
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>字典类型</span>
              <el-button v-if="auth.has('dictionary:manage')" size="small" type="primary" @click="openTypeDialog()">
                新建类型
              </el-button>
            </div>
          </template>
          <el-table v-loading="typeLoading" :data="types" highlight-current-row @current-change="onTypeSelect">
            <el-table-column prop="dictCode" label="编码" width="120" />
            <el-table-column prop="dictName" label="名称" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="auth.has('dictionary:manage')" label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click.stop="openTypeDialog(row)">编辑</el-button>
                <el-dropdown trigger="click" @command="(cmd: string) => onTypeCommand(cmd, row)">
                  <el-button link type="primary" @click.stop>更多</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="toggle">{{ row.enabled ? '停用' : '启用' }}</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无字典类型" />
            </template>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ currentType ? `字典项 - ${currentType.dictName}` : '请选择左侧字典类型' }}</span>
              <el-button v-if="auth.has('dictionary:manage') && currentType" size="small" type="primary"
                @click="openItemDialog()">新建字典项</el-button>
            </div>
          </template>
          <el-table v-loading="itemLoading" :data="items">
            <el-table-column prop="itemCode" label="项编码" width="140" />
            <el-table-column prop="itemLabel" label="项名称" />
            <el-table-column prop="sortOrder" label="排序" width="80" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="auth.has('dictionary:manage')" label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openItemDialog(row)">编辑</el-button>
                <el-button link :type="row.enabled ? 'danger' : 'success'" @click="onToggleItem(row)">
                  {{ row.enabled ? '停用' : '启用' }}
                </el-button>
                <el-button link type="danger" @click="onDeleteItem(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无字典项" />
            </template>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="typeVisible" :title="editingType ? '编辑字典类型' : '新建字典类型'" width="420px" destroy-on-close>
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="90px">
        <el-form-item label="字典编码" prop="dictCode">
          <el-input v-model="typeForm.dictCode" :disabled="!!editingType" />
          <div v-if="editingType" class="form-hint">编码被业务字段引用，创建后不可修改</div>
        </el-form-item>
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="typeForm.dictName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveType">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="itemVisible" :title="editingItem ? '编辑字典项' : '新建字典项'" width="420px" destroy-on-close>
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="90px">
        <el-form-item label="项编码" prop="itemCode">
          <el-input v-model="itemForm.itemCode" />
        </el-form-item>
        <el-form-item label="项名称" prop="itemLabel">
          <el-input v-model="itemForm.itemLabel" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="itemForm.sortOrder" :min="0" :max="9999" style="width: 160px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  changeDictItemStatus,
  changeDictTypeStatus,
  createDictItem,
  createDictType,
  deleteDictItem,
  deleteDictType,
  listDictItems,
  listDictTypes,
  updateDictItem,
  updateDictType
} from '@/api/dictionary'
import type { DictionaryItem, DictionaryType } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const types = ref<DictionaryType[]>([])
const typeLoading = ref(false)
const currentType = ref<DictionaryType | null>(null)
const items = ref<DictionaryItem[]>([])
const itemLoading = ref(false)

const typeVisible = ref(false)
const editingType = ref<DictionaryType | null>(null)
const itemVisible = ref(false)
const editingItem = ref<DictionaryItem | null>(null)
const saving = ref(false)
const typeFormRef = ref<FormInstance>()
const itemFormRef = ref<FormInstance>()

const typeForm = reactive({ dictCode: '', dictName: '' })
const typeRules: FormRules = {
  dictCode: [{ required: true, message: '请输入字典编码', trigger: 'blur' }],
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }]
}
const itemForm = reactive({ itemCode: '', itemLabel: '', sortOrder: 0 })
const itemRules: FormRules = {
  itemCode: [{ required: true, message: '请输入项编码', trigger: 'blur' }],
  itemLabel: [{ required: true, message: '请输入项名称', trigger: 'blur' }]
}

async function loadTypes() {
  typeLoading.value = true
  try {
    types.value = await listDictTypes()
  } finally {
    typeLoading.value = false
  }
}

async function loadItems() {
  if (!currentType.value) return
  itemLoading.value = true
  try {
    items.value = await listDictItems(currentType.value.dictCode)
  } finally {
    itemLoading.value = false
  }
}

function onTypeSelect(row: DictionaryType | null) {
  currentType.value = row
  items.value = []
  if (row) loadItems()
}

function openTypeDialog(row?: DictionaryType) {
  editingType.value = row || null
  Object.assign(typeForm, { dictCode: row?.dictCode || '', dictName: row?.dictName || '' })
  typeVisible.value = true
}

async function onTypeCommand(command: string, row: DictionaryType) {
  if (command === 'toggle') {
    await ElMessageBox.confirm(`确定${row.enabled ? '停用' : '启用'}字典「${row.dictName}」吗？`, '提示', {
      type: 'warning'
    })
    await changeDictTypeStatus(row.id, !row.enabled)
    ElMessage.success('操作成功')
    loadTypes()
    return
  }
  await ElMessageBox.confirm(
    `确定删除字典「${row.dictName}」吗？删除后不可恢复，仍有字典项时会被拒绝。`,
    '删除确认',
    { type: 'warning' }
  )
  await deleteDictType(row.id)
  ElMessage.success('字典已删除')
  if (currentType.value?.id === row.id) {
    currentType.value = null
    items.value = []
  }
  loadTypes()
}

async function onDeleteItem(row: DictionaryItem) {
  await ElMessageBox.confirm(`确定删除字典项「${row.itemLabel}」吗？已被员工档案使用时会被拒绝。`, '删除确认', {
    type: 'warning'
  })
  await deleteDictItem(row.id)
  ElMessage.success('字典项已删除')
  loadItems()
}

async function onSaveType() {
  await typeFormRef.value?.validate()
  saving.value = true
  try {
    const body = { dictCode: typeForm.dictCode, dictName: typeForm.dictName }
    if (editingType.value) {
      await updateDictType(editingType.value.id, body)
      ElMessage.success('字典类型已更新')
    } else {
      await createDictType(body)
      ElMessage.success('字典类型已创建')
    }
    typeVisible.value = false
    loadTypes()
  } finally {
    saving.value = false
  }
}

function openItemDialog(row?: DictionaryItem) {
  editingItem.value = row || null
  if (row) {
    Object.assign(itemForm, { itemCode: row.itemCode, itemLabel: row.itemLabel, sortOrder: row.sortOrder ?? 0 })
  } else {
    Object.assign(itemForm, { itemCode: '', itemLabel: '', sortOrder: 0 })
  }
  itemVisible.value = true
}

async function onSaveItem() {
  await itemFormRef.value?.validate()
  saving.value = true
  try {
    const body = { itemCode: itemForm.itemCode, itemLabel: itemForm.itemLabel, sortOrder: itemForm.sortOrder }
    if (editingItem.value) {
      await updateDictItem(editingItem.value.id, body)
      ElMessage.success('字典项已更新')
    } else {
      await createDictItem(currentType.value!.dictCode, body)
      ElMessage.success('字典项已创建')
    }
    itemVisible.value = false
    loadItems()
  } finally {
    saving.value = false
  }
}

async function onToggleItem(row: DictionaryItem) {
  await ElMessageBox.confirm(`确定${row.enabled ? '停用' : '启用'}字典项「${row.itemLabel}」吗？`, '提示', {
    type: 'warning'
  })
  await changeDictItemStatus(row.id, !row.enabled)
  ElMessage.success('操作成功')
  loadItems()
}

onMounted(loadTypes)
</script>

<style scoped>
.form-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
