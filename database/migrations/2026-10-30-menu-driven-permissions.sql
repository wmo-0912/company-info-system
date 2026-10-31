-- 菜单改由权限点驱动（第 14 轮）
-- 背景：侧边栏此前在前端硬编码，sys_permission 里的 route_path / icon / parent 形同虚设；
--       且 user:read 等 5 个菜单的 parent 指向并不存在的 'system' 节点，parent_id 全部落空。
-- 本脚本把权限点整理成前端真实路由的菜单树，供 GET /api/v1/menus 下发。
-- 执行前建议先备份：pg_dump -Fc company_info > before-menu.sql
BEGIN;

-- 1. 补齐「系统管理」分组节点
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, route_path, icon, sort_order, enabled)
SELECT 'system', '系统管理', 'MENU', NULL, NULL, 'Setting', 7, TRUE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'system');

-- 2. 系统管理下的 5 个菜单挂到该分组
UPDATE sys_permission
SET parent_id = (SELECT id FROM sys_permission WHERE permission_code = 'system'),
    permission_name = CASE permission_code
        WHEN 'user:read' THEN '用户管理'
        WHEN 'role:read' THEN '角色管理'
        WHEN 'dictionary:read' THEN '数据字典'
        WHEN 'system:config:read' THEN '系统配置'
        ELSE permission_name END
WHERE permission_code IN ('user:read', 'role:read', 'dictionary:read', 'system:config:read', 'audit:read');

-- 3. 路由对齐前端实际路由（原值 /dashboard、/organization/departments、/employment/requests 在前端都不存在）
UPDATE sys_permission SET route_path = '/', icon = 'Odometer', permission_name = '首页仪表盘' WHERE permission_code = 'dashboard:view';
UPDATE sys_permission SET route_path = NULL, icon = 'OfficeBuilding', permission_name = '组织架构' WHERE permission_code = 'organization';
UPDATE sys_permission SET route_path = '/departments', permission_name = '部门管理', parent_id = (SELECT id FROM sys_permission WHERE permission_code = 'organization') WHERE permission_code = 'department:read';
UPDATE sys_permission SET route_path = '/employees', permission_name = '员工档案', parent_id = (SELECT id FROM sys_permission WHERE permission_code = 'organization') WHERE permission_code = 'employee:read';
UPDATE sys_permission SET route_path = '/employment', permission_name = '入转调离' WHERE permission_code = 'employment:submit';
UPDATE sys_permission SET route_path = '/projects', permission_name = '项目组' WHERE permission_code = 'project:read';
UPDATE sys_permission SET route_path = '/performance', permission_name = '绩效考核' WHERE permission_code = 'performance:read';
UPDATE sys_permission SET route_path = '/profile', icon = 'Postcard' WHERE permission_code = 'profile:read';

COMMIT;
