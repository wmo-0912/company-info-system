import { apiGet, apiPost } from './http'
import type { CurrentUser, LoginResponse } from './types'

export function login(username: string, password: string) {
  return apiPost<LoginResponse>('/auth/login', { username, password })
}

export function fetchMe() {
  return apiGet<CurrentUser>('/auth/me')
}

export function logoutApi() {
  return apiPost<void>('/auth/logout')
}
