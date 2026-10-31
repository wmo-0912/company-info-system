import axios, { AxiosError, type AxiosInstance, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from './types'

const http: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000
})

/** 同类错误在冷却期内只提示一次，避免后端掉线时大量并发请求反复弹窗刷屏 */
const ERROR_COOLDOWN_MS = 5000
const noticeAt = new Map<string, number>()

function showErrorOnce(key: string, message: string, cooldownMs = ERROR_COOLDOWN_MS) {
  const now = Date.now()
  const last = noticeAt.get(key) ?? 0
  if (now - last < cooldownMs) return
  noticeAt.set(key, now)
  ElMessage.error({ message, grouping: true })
}

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse<unknown>>) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '请求失败'
    if (status === 401) {
      localStorage.removeItem('token')
      if (window.location.pathname !== '/login') {
        showErrorOnce('401', '登录已过期，请重新登录')
        window.location.href = '/login'
      }
    } else if (status === 403) {
      showErrorOnce('403', '无权限执行该操作')
    } else if (!status) {
      // 后端未启动 / 掉线 / 断网：统一提示一次，10 秒内不再重复
      const offline = error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '无法连接服务器，请检查后端服务是否已启动'
      showErrorOnce(error.code === 'ECONNABORTED' ? 'timeout' : 'network', offline, 10000)
    } else if (status >= 500) {
      showErrorOnce('5xx', `服务器异常（${status}），请稍后重试`, 10000)
    } else {
      showErrorOnce(`http_${status}`, message)
    }
    return Promise.reject(error)
  }
)

function unwrap<T>(res: AxiosResponse<ApiResponse<T>>): T {
  if (res.data.code !== 'OK') {
    ElMessage.error({ message: res.data.message || '操作失败', grouping: true })
    throw new Error(res.data.message)
  }
  return res.data.data
}

export async function apiGet<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await http.get<ApiResponse<T>>(url, { params })
  return unwrap(res)
}

export async function apiPost<T>(url: string, body?: unknown): Promise<T> {
  const res = await http.post<ApiResponse<T>>(url, body)
  return unwrap(res)
}

export async function apiPut<T>(url: string, body?: unknown): Promise<T> {
  const res = await http.put<ApiResponse<T>>(url, body)
  return unwrap(res)
}

export async function apiPatch<T>(url: string, body?: unknown): Promise<T> {
  const res = await http.patch<ApiResponse<T>>(url, body)
  return unwrap(res)
}

export async function apiDelete<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await http.delete<ApiResponse<T>>(url, { params })
  return unwrap(res)
}

export default http
