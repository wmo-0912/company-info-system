import axios, { AxiosError, type AxiosInstance, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from './types'

const http: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000
})

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
        ElMessage.error('登录已过期，请重新登录')
        window.location.href = '/login'
      }
    } else if (status === 403) {
      ElMessage.error('无权限执行该操作')
    } else {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

function unwrap<T>(res: AxiosResponse<ApiResponse<T>>): T {
  if (res.data.code !== 'OK') {
    ElMessage.error(res.data.message || '操作失败')
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

export async function apiDelete<T>(url: string): Promise<T> {
  const res = await http.delete<ApiResponse<T>>(url)
  return unwrap(res)
}

export default http
