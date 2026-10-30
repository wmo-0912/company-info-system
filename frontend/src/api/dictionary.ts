import { apiGet, apiPatch, apiPost, apiPut } from './http'
import type { DictionaryItem, DictionaryType } from './types'

export interface DictTypeBody {
  dictCode: string
  dictName: string
}

export interface DictItemBody {
  itemCode: string
  itemLabel: string
  sortOrder?: number | null
}

export function listDictTypes() {
  return apiGet<DictionaryType[]>('/dictionaries')
}

export function createDictType(body: DictTypeBody) {
  return apiPost<DictionaryType>('/dictionaries', body)
}

export function listDictItems(dictCode: string) {
  return apiGet<DictionaryItem[]>(`/dictionaries/${dictCode}/items`)
}

export function createDictItem(dictCode: string, body: DictItemBody) {
  return apiPost<DictionaryItem>(`/dictionaries/${dictCode}/items`, body)
}

export function updateDictItem(id: number, body: DictItemBody) {
  return apiPut<DictionaryItem>(`/dictionary-items/${id}`, body)
}

export function changeDictItemStatus(id: number, enabled: boolean) {
  return apiPatch<void>(`/dictionary-items/${id}/status`, { enabled })
}
