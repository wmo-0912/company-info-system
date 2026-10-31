import { apiGet } from './http'
import type { MenuNode } from './types'

/** 当前用户可见的菜单树，完全由服务端权限点决定 */
export function fetchMyMenus() {
  return apiGet<MenuNode[]>('/menus')
}
