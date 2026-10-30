package com.example.company.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.department.entity.Department;
import com.example.company.department.mapper.DepartmentMapper;
import com.example.company.dictionary.entity.DictionaryItem;
import com.example.company.dictionary.entity.DictionaryType;
import com.example.company.dictionary.mapper.DictionaryItemMapper;
import com.example.company.dictionary.mapper.DictionaryTypeMapper;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.employment.entity.EmployeeEmploymentRecord;
import com.example.company.employment.mapper.EmployeeEmploymentRecordMapper;
import com.example.company.performance.entity.PerformanceReview;
import com.example.company.performance.mapper.PerformanceReviewMapper;
import com.example.company.project.entity.ProjectTeam;
import com.example.company.project.entity.ProjectTeamMember;
import com.example.company.project.mapper.ProjectTeamMapper;
import com.example.company.project.mapper.ProjectTeamMemberMapper;
import com.example.company.role.entity.SysPermission;
import com.example.company.role.entity.SysRole;
import com.example.company.role.entity.SysRolePermission;
import com.example.company.role.entity.SysUserRole;
import com.example.company.role.mapper.SysPermissionMapper;
import com.example.company.role.mapper.SysRoleMapper;
import com.example.company.role.mapper.SysRolePermissionMapper;
import com.example.company.role.mapper.SysUserRoleMapper;
import com.example.company.system.entity.SysConfig;
import com.example.company.system.mapper.SysConfigMapper;
import com.example.company.user.entity.SysUser;
import com.example.company.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 开发环境演示数据初始化器。
 * - 权限点与四角色权限矩阵：每次启动（app.seed-demo-data=true）都按 D01 规格同步，幂等。
 * - 演示业务数据（部门/员工/用户/项目组/字典等）：仅当 sys_user 为空时初始化。
 * 演示密码仅用于本地开发，数据库只存 BCrypt 哈希。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DevDataInitializer implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final DepartmentMapper departmentMapper;
    private final EmployeeMapper employeeMapper;
    private final ProjectTeamMapper teamMapper;
    private final ProjectTeamMemberMapper memberMapper;
    private final DictionaryTypeMapper dictTypeMapper;
    private final DictionaryItemMapper dictItemMapper;
    private final EmployeeEmploymentRecordMapper recordMapper;
    private final PerformanceReviewMapper reviewMapper;
    private final SysConfigMapper configMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed-demo-data:false}")
    private boolean seedDemoData;

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedDemoData) {
            return;
        }
        // 权限点与四角色权限矩阵：每次启动都按规格同步（幂等），
        // 保证历史库的权限分配始终与 D01 需求一致。
        syncRolesAndPermissions();

        // 演示业务数据：仅当库为空时初始化
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        log.info("初始化演示数据（仅开发环境）...");

        // ---------- 部门 ----------
        Department hq = saveDept("HQ", "总公司", null);
        Department rd = saveDept("RD", "研发部", hq.getId());
        Department hrDept = saveDept("HR_DEPT", "人力资源部", hq.getId());
        Department qa = saveDept("QA", "测试部", hq.getId());

        // ---------- 员工 ----------
        SysRole adminRole = roleMapper.selectOne(new QueryWrapper<SysRole>().eq("role_code", "ADMIN"));
        SysRole hrRole = roleMapper.selectOne(new QueryWrapper<SysRole>().eq("role_code", "HR"));
        SysRole pmRole = roleMapper.selectOne(new QueryWrapper<SysRole>().eq("role_code", "PROJECT_MANAGER"));
        SysRole empRole = roleMapper.selectOne(new QueryWrapper<SysRole>().eq("role_code", "EMPLOYEE"));

        Employee adminEmp = saveEmployee("E0001", "系统管理员", rd.getId(), "系统管理员", "L5", null,
                LocalDate.of(2020, 1, 6), "REGULAR");
        Employee hrEmp = saveEmployee("E0002", "王人事", hrDept.getId(), "人事专员", "L3", null,
                LocalDate.of(2021, 3, 15), "REGULAR");
        Employee pmEmp = saveEmployee("E0003", "李项目", rd.getId(), "项目经理", "L4", null,
                LocalDate.of(2020, 7, 1), "REGULAR");
        Employee zhang = saveEmployee("E0101", "张三", rd.getId(), "后端工程师", "L2", pmEmp.getId(),
                LocalDate.of(2023, 2, 20), "REGULAR");
        Employee li = saveEmployee("E0102", "李四", rd.getId(), "前端工程师", "L2", pmEmp.getId(),
                LocalDate.of(2024, 6, 10), "PROBATION");
        Employee wang = saveEmployee("E0103", "王五", qa.getId(), "测试工程师", "L2", null,
                LocalDate.of(2022, 11, 8), "REGULAR");
        rd.setManagerEmployeeId(pmEmp.getId());
        departmentMapper.updateById(rd);
        hrDept.setManagerEmployeeId(hrEmp.getId());
        departmentMapper.updateById(hrDept);

        // ---------- 用户 ----------
        SysUser adminUser = saveUser("admin", "Admin@123", adminEmp.getId(), adminRole.getId());
        saveUser("hr", "Hr@123456", hrEmp.getId(), hrRole.getId());
        SysUser pmUser = saveUser("pm", "Pm@123456", pmEmp.getId(), pmRole.getId());
        saveUser("zhangsan", "Emp@123456", zhang.getId(), empRole.getId());
        saveUser("lisi", "Emp@123456", li.getId(), empRole.getId());
        saveUser("wangwu", "Emp@123456", wang.getId(), empRole.getId());

        // ---------- 项目组 ----------
        ProjectTeam alpha = new ProjectTeam();
        alpha.setProjectCode("PRJ-ALPHA");
        alpha.setProjectName("阿尔法平台研发组");
        alpha.setManagerEmployeeId(pmEmp.getId());
        alpha.setDescription("核心平台研发项目组");
        alpha.setStatus("ACTIVE");
        teamMapper.insert(alpha);
        ProjectTeam beta = new ProjectTeam();
        beta.setProjectCode("PRJ-BETA");
        beta.setProjectName("贝塔数据项目组");
        beta.setManagerEmployeeId(pmEmp.getId());
        beta.setDescription("数据平台项目组");
        beta.setStatus("ACTIVE");
        teamMapper.insert(beta);
        saveMember(alpha.getId(), zhang.getId(), "后端开发", LocalDate.of(2025, 1, 5));
        saveMember(alpha.getId(), li.getId(), "前端开发", LocalDate.of(2025, 3, 1));
        saveMember(beta.getId(), wang.getId(), "测试", LocalDate.of(2025, 2, 10));

        // ---------- 字典 ----------
        DictionaryType edu = saveDictType("EDUCATION", "学历");
        saveDictItem(edu.getId(), "COLLEGE", "大专", 1);
        saveDictItem(edu.getId(), "BACHELOR", "本科", 2);
        saveDictItem(edu.getId(), "MASTER", "硕士", 3);
        saveDictItem(edu.getId(), "DOCTOR", "博士", 4);
        DictionaryType level = saveDictType("POSITION_LEVEL", "职位层级");
        saveDictItem(level.getId(), "L1", "初级", 1);
        saveDictItem(level.getId(), "L2", "中级", 2);
        saveDictItem(level.getId(), "L3", "高级", 3);
        saveDictItem(level.getId(), "L4", "资深", 4);
        saveDictItem(level.getId(), "L5", "专家", 5);
        DictionaryType gender = saveDictType("GENDER", "性别");
        saveDictItem(gender.getId(), "MALE", "男", 1);
        saveDictItem(gender.getId(), "FEMALE", "女", 2);

        // ---------- 任职历史示例 ----------
        saveRecord(zhang.getId(), "ONBOARD", zhang.getHireDate(), null, rd.getId(), null, "后端工程师", "入职", adminUser.getId());
        saveRecord(zhang.getId(), "REGULARIZE", zhang.getHireDate().plusMonths(6), rd.getId(), rd.getId(),
                "后端工程师", "后端工程师", "试用期满转正", hrEmpUserId());

        // ---------- 绩效示例 ----------
        PerformanceReview review = new PerformanceReview();
        review.setEmployeeId(zhang.getId());
        review.setReviewerEmployeeId(pmEmp.getId());
        review.setProjectTeamId(alpha.getId());
        review.setPeriodType("MONTHLY");
        review.setPeriodKey("2026-09");
        review.setScore(new BigDecimal("88.50"));
        review.setRating("A");
        review.setComment("交付质量稳定，协作积极。");
        review.setStatus("SUBMITTED");
        review.setSubmittedAt(OffsetDateTime.now().minusDays(5));
        reviewMapper.insert(review);

        PerformanceReview draft = new PerformanceReview();
        draft.setEmployeeId(li.getId());
        draft.setReviewerEmployeeId(pmEmp.getId());
        draft.setProjectTeamId(alpha.getId());
        draft.setPeriodType("QUARTERLY");
        draft.setPeriodKey("2026-Q3");
        draft.setComment("待补充");
        draft.setStatus("DRAFT");
        reviewMapper.insert(draft);

        // ---------- 系统配置 ----------
        SysConfig company = new SysConfig();
        company.setConfigKey("company.name");
        company.setConfigValue("示例科技有限公司");
        company.setDescription("公司名称");
        company.setUpdatedAt(OffsetDateTime.now());
        configMapper.insert(company);
        SysConfig logo = new SysConfig();
        logo.setConfigKey("company.logo.url");
        logo.setConfigValue("");
        logo.setDescription("公司 Logo URL（第一阶段仅支持 URL）");
        logo.setUpdatedAt(OffsetDateTime.now());
        configMapper.insert(logo);

        log.info("演示数据初始化完成。演示账号：admin/hr/pm/zhangsan/lisi/wangwu（密码见 README，仅开发环境）");
    }

    /**
     * 同步权限点与四角色权限矩阵（幂等）。
     * 权限点按 permission_code upsert；四角色按 role_code upsert；
     * 角色-权限绑定先删后插，确保与 D01 规格完全一致。
     */
    private void syncRolesAndPermissions() {
        record Perm(String code, String name, String type, String parent, String route, String icon, int sort) {}
        List<Perm> defs = List.of(
                new Perm("dashboard:view", "首页仪表盘", "MENU", null, "/", "Odometer", 1),
                new Perm("organization", "组织架构", "MENU", null, null, "OfficeBuilding", 2),
                new Perm("department:read", "部门管理", "MENU", "organization", "/departments", null, 21),
                new Perm("department:create", "部门新增", "BUTTON", "department:read", null, null, 22),
                new Perm("department:update", "部门编辑", "BUTTON", "department:read", null, null, 23),
                new Perm("department:disable", "部门停用", "BUTTON", "department:read", null, null, 24),
                new Perm("employee:read", "员工档案", "MENU", "organization", "/employees", null, 31),
                new Perm("employee:create", "员工新增", "BUTTON", "employee:read", null, null, 32),
                new Perm("employee:update", "员工编辑", "BUTTON", "employee:read", null, null, 33),
                new Perm("employee:archive", "员工归档", "BUTTON", "employee:read", null, null, 34),
                new Perm("employment:submit", "入转调离", "MENU", null, "/employment", "Switch", 4),
                new Perm("employment:process", "入转调离办理", "BUTTON", "employment:submit", null, null, 41),
                new Perm("project:read", "项目组", "MENU", null, "/projects", "Folder", 5),
                new Perm("project:create", "项目组新增", "BUTTON", "project:read", null, null, 51),
                new Perm("project:update", "项目组编辑", "BUTTON", "project:read", null, null, 52),
                new Perm("project:member:manage", "成员管理", "BUTTON", "project:read", null, null, 53),
                new Perm("performance:read", "绩效考核", "MENU", null, "/performance", "TrendCharts", 6),
                new Perm("performance:create", "绩效创建", "BUTTON", "performance:read", null, null, 61),
                new Perm("performance:update", "绩效编辑", "BUTTON", "performance:read", null, null, 62),
                new Perm("performance:submit", "绩效提交", "BUTTON", "performance:read", null, null, 63),
                new Perm("performance:review", "绩效复核", "BUTTON", "performance:read", null, null, 64),
                new Perm("system", "系统管理", "MENU", null, null, "Setting", 7),
                new Perm("user:read", "用户管理", "MENU", "system", "/system/users", null, 71),
                new Perm("user:create", "用户新增", "BUTTON", "user:read", null, null, 72),
                new Perm("user:update", "用户编辑", "BUTTON", "user:read", null, null, 73),
                new Perm("user:disable", "用户启停", "BUTTON", "user:read", null, null, 74),
                new Perm("role:read", "角色管理", "MENU", "system", "/system/roles", null, 75),
                new Perm("role:manage", "角色管理", "BUTTON", "role:read", null, null, 76),
                new Perm("permission:manage", "权限点管理", "BUTTON", "role:read", null, null, 77),
                new Perm("dictionary:read", "数据字典", "MENU", "system", "/system/dictionaries", null, 78),
                new Perm("dictionary:manage", "字典管理", "BUTTON", "dictionary:read", null, null, 79),
                new Perm("system:config:read", "系统配置", "MENU", "system", "/system/config", null, 80),
                new Perm("system:config:update", "配置更新", "BUTTON", "system:config:read", null, null, 81),
                new Perm("audit:read", "审计日志", "MENU", "system", "/system/audit-logs", null, 82),
                new Perm("profile:read", "个人中心", "MENU", null, "/profile", "Postcard", 8),
                new Perm("profile:update", "个人资料修改", "BUTTON", "profile:read", null, null, 83)
        );

        // 1. upsert 权限点
        Map<String, Long> permIds = new java.util.HashMap<>();
        for (Perm def : defs) {
            SysPermission existing = permissionMapper.selectOne(
                    new QueryWrapper<SysPermission>().eq("permission_code", def.code()));
            Long parentId = def.parent() == null ? null : permIds.get(def.parent());
            if (existing == null) {
                SysPermission p = new SysPermission();
                p.setPermissionCode(def.code());
                p.setPermissionName(def.name());
                p.setPermissionType(def.type());
                p.setParentId(parentId);
                p.setRoutePath(def.route());
                p.setIcon(def.icon());
                p.setSortOrder(def.sort());
                p.setEnabled(true);
                permissionMapper.insert(p);
                permIds.put(def.code(), p.getId());
            } else {
                existing.setPermissionName(def.name());
                existing.setPermissionType(def.type());
                existing.setParentId(parentId);
                existing.setRoutePath(def.route());
                existing.setIcon(def.icon());
                existing.setSortOrder(def.sort());
                existing.setEnabled(true);
                permissionMapper.updateById(existing);
                permIds.put(def.code(), existing.getId());
            }
        }

        // 2. upsert 四角色（数据范围严格按 D01）
        SysRole admin = upsertRole("ADMIN", "管理员", "系统管理员", "ALL");
        SysRole hr = upsertRole("HR", "人事管理", "人事管理专员", "ALL");
        SysRole pm = upsertRole("PROJECT_MANAGER", "项目经理", "项目负责人", "PROJECT");
        SysRole empRole = upsertRole("EMPLOYEE", "普通员工", "普通员工", "SELF");

        // 3. 重绑角色-权限：先清除四角色旧绑定，再按规格矩阵写入
        List<Long> builtinRoleIds = List.of(admin.getId(), hr.getId(), pm.getId(), empRole.getId());
        rolePermissionMapper.delete(new QueryWrapper<SysRolePermission>().in("role_id", builtinRoleIds));

        // ADMIN：系统配置、权限管理（角色/用户/账号状态）、数据字典、组织架构、审计
        bindPerms(admin.getId(), List.of(
                "dashboard:view", "organization",
                "department:read", "department:create", "department:update", "department:disable",
                "employee:read",
                "user:read", "user:create", "user:update", "user:disable",
                "role:read", "role:manage", "permission:manage",
                "dictionary:read", "dictionary:manage",
                "performance:read", "performance:review",
                "system:config:read", "system:config:update",
                "audit:read",
                "profile:read", "profile:update"), permIds);

        // HR：员工档案管理、入转调离办理（发起并办理）
        bindPerms(hr.getId(), List.of(
                "dashboard:view", "organization",
                "department:read",
                "employee:read", "employee:create", "employee:update", "employee:archive",
                "employment:submit", "employment:process",
                "performance:read", "performance:review",
                "dictionary:read",
                "profile:read", "profile:update"), permIds);

        // PM：团队管理（查看所属项目组成员）、绩效/考核
        bindPerms(pm.getId(), List.of(
                "dashboard:view", "organization",
                "department:read", "employee:read",
                "project:read", "project:update", "project:member:manage",
                "performance:read", "performance:create", "performance:update", "performance:submit",
                "dictionary:read",
                "profile:read", "profile:update"), permIds);

        // EMPLOYEE：个人中心（查看/修改个人信息，查看自己的电子档案——含所在项目与本人绩效）
        bindPerms(empRole.getId(), List.of(
                "dashboard:view",
                "project:read", "performance:read",
                "dictionary:read",
                "profile:read", "profile:update"), permIds);

        log.info("已同步权限点({})与四角色权限矩阵（按 D01 规格）", permIds.size());
    }

    private Long hrEmpUserId() {
        SysUser u = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", "hr"));
        return u == null ? null : u.getId();
    }

    private SysRole upsertRole(String code, String name, String desc, String scope) {
        SysRole role = roleMapper.selectOne(new QueryWrapper<SysRole>().eq("role_code", code));
        if (role == null) {
            role = new SysRole();
            role.setRoleCode(code);
            role.setRoleName(name);
            role.setDescription(desc);
            role.setDataScope(scope);
            role.setEnabled(true);
            roleMapper.insert(role);
        } else {
            role.setRoleName(name);
            role.setDescription(desc);
            role.setDataScope(scope);
            role.setEnabled(true);
            roleMapper.updateById(role);
        }
        return role;
    }

    private void bindPerms(Long roleId, List<String> codes, Map<String, Long> permIds) {
        for (String code : codes) {
            Long pid = permIds.get(code);
            if (pid == null) {
                log.warn("权限点未定义，跳过角色绑定: {}", code);
                continue;
            }
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(pid);
            rolePermissionMapper.insert(rp);
        }
    }

    private Department saveDept(String code, String name, Long parentId) {
        Department dept = new Department();
        dept.setDepartmentCode(code);
        dept.setDepartmentName(name);
        dept.setParentId(parentId);
        dept.setStatus("ACTIVE");
        departmentMapper.insert(dept);
        return dept;
    }

    private Employee saveEmployee(String no, String name, Long deptId, String position, String level,
                                  Long managerId, LocalDate hireDate, String status) {
        Employee e = new Employee();
        e.setEmployeeNo(no);
        e.setEmployeeName(name);
        e.setDepartmentId(deptId);
        e.setPositionName(position);
        e.setPositionLevelCode(level);
        e.setManagerEmployeeId(managerId);
        e.setHireDate(hireDate);
        e.setEmploymentStatus(status);
        e.setEducationCode("BACHELOR");
        employeeMapper.insert(e);
        return e;
    }

    private SysUser saveUser(String username, String rawPassword, Long employeeId, Long roleId) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setEmployeeId(employeeId);
        user.setStatus("ACTIVE");
        // 演示账号密码写在 README 中，不强制改密，否则本地联调无法继续
        user.setMustChangePassword(false);
        userMapper.insert(user);
        SysUserRole ur = new SysUserRole();
        ur.setUserId(user.getId());
        ur.setRoleId(roleId);
        userRoleMapper.insert(ur);
        return user;
    }

    private void saveMember(Long teamId, Long employeeId, String role, LocalDate joinedAt) {
        ProjectTeamMember m = new ProjectTeamMember();
        m.setProjectTeamId(teamId);
        m.setEmployeeId(employeeId);
        m.setMemberRole(role);
        m.setJoinedAt(joinedAt);
        memberMapper.insert(m);
    }

    private DictionaryType saveDictType(String code, String name) {
        DictionaryType t = new DictionaryType();
        t.setDictCode(code);
        t.setDictName(name);
        t.setEnabled(true);
        dictTypeMapper.insert(t);
        return t;
    }

    private void saveDictItem(Long typeId, String code, String label, int sort) {
        DictionaryItem item = new DictionaryItem();
        item.setDictTypeId(typeId);
        item.setItemCode(code);
        item.setItemLabel(label);
        item.setSortOrder(sort);
        item.setEnabled(true);
        dictItemMapper.insert(item);
    }

    private void saveRecord(Long employeeId, String type, LocalDate date, Long oldDept, Long newDept,
                            String oldPos, String newPos, String reason, Long operatorUserId) {
        EmployeeEmploymentRecord r = new EmployeeEmploymentRecord();
        r.setEmployeeId(employeeId);
        r.setChangeType(type);
        r.setEffectiveDate(date);
        r.setOldDepartmentId(oldDept);
        r.setNewDepartmentId(newDept);
        r.setOldPositionName(oldPos);
        r.setNewPositionName(newPos);
        r.setReason(reason);
        r.setOperatorUserId(operatorUserId);
        recordMapper.insert(r);
    }
}
