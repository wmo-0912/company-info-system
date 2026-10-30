import { ref } from 'vue'
import { pageEmployees } from '@/api/employee'
import type { EmployeeListItem } from '@/api/types'

const PAGE_SIZE = 100
const MAX_PAGES = 10

/**
 * 员工下拉选项：分页拉全量（D02 约定公司规模 ≤200 人），
 * 避免各页面固定 `pageSize: 100` 时超出部分被静默截断而无法选中。
 */
export function useEmployeeOptions() {
  const options = ref<EmployeeListItem[]>([])
  const loading = ref(false)

  async function load(force = false) {
    if (!force && options.value.length) {
      return options.value
    }
    loading.value = true
    try {
      const collected: EmployeeListItem[] = []
      for (let page = 1; page <= MAX_PAGES; page++) {
        const res = await pageEmployees({ page, pageSize: PAGE_SIZE })
        collected.push(...res.records)
        if (collected.length >= res.total || res.records.length === 0) {
          break
        }
      }
      options.value = collected
      return collected
    } finally {
      loading.value = false
    }
  }

  return { options, loading, load }
}
