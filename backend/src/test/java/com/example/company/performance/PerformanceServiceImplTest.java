package com.example.company.performance;

import com.example.company.audit.service.AuditService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.company.common.enums.ReviewStatus;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.DataScopeService;
import com.example.company.common.security.LoginUser;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.performance.dto.PerformanceReviewRequest;
import com.example.company.performance.entity.PerformanceReview;
import com.example.company.performance.mapper.PerformanceReviewMapper;
import com.example.company.performance.service.impl.PerformanceServiceImpl;
import com.example.company.project.entity.ProjectTeam;
import com.example.company.project.entity.ProjectTeamMember;
import com.example.company.project.mapper.ProjectTeamMapper;
import com.example.company.project.mapper.ProjectTeamMemberMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceServiceImplTest {

    @Mock
    private PerformanceReviewMapper reviewMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private ProjectTeamMapper teamMapper;
    @Mock
    private ProjectTeamMemberMapper memberMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private DataScopeService dataScopeService;

    @InjectMocks
    private PerformanceServiceImpl service;

    @BeforeEach
    void setUpPm() {
        LoginUser pm = new LoginUser();
        pm.setUserId(3L);
        pm.setUsername("pm");
        pm.setEmployeeId(7L);
        pm.setRoles(Set.of("PROJECT_MANAGER"));
        pm.setPermissions(Set.of("performance:read", "performance:create", "performance:update", "performance:submit"));
        pm.setDataScope("PROJECT");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(pm, null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private PerformanceReviewRequest newRequest(Long employeeId, Long teamId) {
        PerformanceReviewRequest req = new PerformanceReviewRequest();
        req.setEmployeeId(employeeId);
        req.setProjectTeamId(teamId);
        req.setPeriodType("MONTHLY");
        req.setPeriodKey("2026-10");
        req.setScore(new java.math.BigDecimal("90"));
        req.setComment("good");
        return req;
    }

    @Test
    void cannotReviewSelf() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(newRequest(7L, 100L)));
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    void pmCannotReviewEmployeeOutsideManagedTeams() {
        Employee target = new Employee();
        target.setId(99L);
        when(employeeMapper.selectById(99L)).thenReturn(target);
        ProjectTeam team = new ProjectTeam();
        team.setId(100L);
        team.setManagerEmployeeId(7L);
        when(teamMapper.selectList(any())).thenReturn(List.of(team));
        when(memberMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(newRequest(99L, 100L)));
        assertEquals(403, ex.getHttpStatus());
    }

    @Test
    void pmCanReviewTeamMember() {
        Employee target = new Employee();
        target.setId(11L);
        when(employeeMapper.selectById(11L)).thenReturn(target);
        ProjectTeam team = new ProjectTeam();
        team.setId(100L);
        team.setManagerEmployeeId(7L);
        when(teamMapper.selectList(any())).thenReturn(List.of(team));
        when(memberMapper.selectCount(any())).thenReturn(1L);
        when(reviewMapper.selectCount(any())).thenReturn(0L);
        when(teamMapper.selectById(100L)).thenReturn(team);

        var vo = service.create(newRequest(11L, 100L));
        assertNotNull(vo);
        org.mockito.Mockito.verify(reviewMapper).insert(org.mockito.ArgumentMatchers.argThat(
                (PerformanceReview r) -> r.getReviewerEmployeeId().equals(7L)));
    }

    @Test
    void submittedReviewCannotBeEdited() {
        PerformanceReview review = new PerformanceReview();
        review.setId(1L);
        review.setReviewerEmployeeId(7L);
        review.setEmployeeId(11L);
        review.setStatus(ReviewStatus.SUBMITTED.name());
        when(reviewMapper.selectById(1L)).thenReturn(review);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(1L, newRequest(11L, 100L)));
        assertEquals(422, ex.getHttpStatus());
    }

    @Test
    void submitRequiresScore() {
        PerformanceReview review = new PerformanceReview();
        review.setId(1L);
        review.setReviewerEmployeeId(7L);
        review.setEmployeeId(11L);
        review.setStatus(ReviewStatus.DRAFT.name());
        when(reviewMapper.selectById(1L)).thenReturn(review);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.submit(1L));
        assertEquals(422, ex.getHttpStatus());
    }

    @Test
    void cannotEditOthersReview() {
        PerformanceReview review = new PerformanceReview();
        review.setId(1L);
        review.setReviewerEmployeeId(999L);
        review.setEmployeeId(11L);
        review.setStatus(ReviewStatus.DRAFT.name());
        when(reviewMapper.selectById(1L)).thenReturn(review);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(1L, newRequest(11L, 100L)));
        assertEquals(403, ex.getHttpStatus());
    }

    private LoginUser loginUser(Long employeeId, String scope, String... perms) {
        LoginUser u = new LoginUser();
        u.setUserId(9L);
        u.setUsername("u");
        u.setEmployeeId(employeeId);
        u.setDataScope(scope);
        u.setPermissions(Set.of(perms));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, List.of()));
        return u;
    }

    private PerformanceReview review(Long id, Long employeeId, Long reviewerId, String status) {
        PerformanceReview r = new PerformanceReview();
        r.setId(id);
        r.setEmployeeId(employeeId);
        r.setReviewerEmployeeId(reviewerId);
        r.setPeriodType("MONTHLY");
        r.setPeriodKey("2026-10");
        r.setStatus(status);
        return r;
    }

    @Test
    @DisplayName("没有任何可见员工时列表直接返回空页，不查库")
    void pageReturnsEmptyWhenNoVisibleEmployees() {
        loginUser(7L, "SELF");
        when(dataScopeService.visibleEmployeeIds(any())).thenReturn(Set.of());

        var page = service.page(1, 10, null, null, null);

        assertEquals(0, page.getTotal());
        assertEquals(List.of(), page.getRecords());
        verify(reviewMapper, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("列表按统一数据范围过滤：本人评价 + 范围内被评价的非草稿记录")
    void pageAppliesUnifiedDataScope() {
        loginUser(7L, "PROJECT");
        when(dataScopeService.visibleEmployeeIds(any())).thenReturn(Set.of(7L, 11L));
        when(reviewMapper.selectPage(any(), any())).thenReturn(new Page<>());

        service.page(1, 10, null, null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PerformanceReview>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(reviewMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("reviewer_employee_id"), sql);
        assertTrue(sql.contains("employee_id"), sql);
        assertTrue(sql.contains("status"), sql);
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("DRAFT"), sql);
    }

    @Test
    @DisplayName("详情：范围外的记录 403")
    void detailRejectsRecordOutsideScope() {
        loginUser(7L, "PROJECT");
        when(dataScopeService.visibleEmployeeIds(any())).thenReturn(Set.of(7L));
        when(reviewMapper.selectById(1L)).thenReturn(review(1L, 11L, 999L, ReviewStatus.SUBMITTED.name()));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail(1L));
        assertEquals(403, ex.getHttpStatus());
    }

    @Test
    @DisplayName("详情：被评价人看不到草稿")
    void detailHidesDraftFromSubject() {
        loginUser(11L, "SELF");
        when(dataScopeService.visibleEmployeeIds(any())).thenReturn(Set.of(11L));
        when(reviewMapper.selectById(1L)).thenReturn(review(1L, 11L, 7L, ReviewStatus.DRAFT.name()));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail(1L));
        assertEquals(403, ex.getHttpStatus());
    }

    @Test
    @DisplayName("详情：被评价人可见已提交评价")
    void detailAllowsSubmittedForSubject() {
        loginUser(11L, "SELF");
        when(dataScopeService.visibleEmployeeIds(any())).thenReturn(Set.of(11L));
        PerformanceReview r = review(1L, 11L, 7L, ReviewStatus.SUBMITTED.name());
        when(reviewMapper.selectById(1L)).thenReturn(r);
        when(employeeMapper.selectById(11L)).thenReturn(null);
        when(employeeMapper.selectById(7L)).thenReturn(null);

        var vo = service.detail(1L);
        assertEquals(ReviewStatus.SUBMITTED.name(), vo.getStatus());
    }

    @Test
    @DisplayName("人事退回：状态置 RETURNED 并写审计原因")
    void returnForRevisionMarksReturned() {
        loginUser(5L, "ALL", "performance:read", "performance:review");
        PerformanceReview r = review(1L, 11L, 7L, ReviewStatus.SUBMITTED.name());
        when(reviewMapper.selectById(1L)).thenReturn(r);
        when(employeeMapper.selectById(11L)).thenReturn(null);
        when(employeeMapper.selectById(7L)).thenReturn(null);

        var vo = service.returnForRevision(1L, "评分与考勤不符");

        assertEquals(ReviewStatus.RETURNED.name(), vo.getStatus());
        verify(reviewMapper).updateById(r);
        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(auditService).success(eq("performance:return"), eq("performance_review"), eq(1L), summary.capture());
        assertTrue(summary.getValue().contains("评分与考勤不符"));
    }

    @Test
    @DisplayName("人事退回：仅已提交的评价可退回")
    void returnForRevisionRejectsDraft() {
        loginUser(5L, "ALL", "performance:read", "performance:review");
        PerformanceReview r = review(1L, 11L, 7L, ReviewStatus.DRAFT.name());
        when(reviewMapper.selectById(1L)).thenReturn(r);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.returnForRevision(1L, null));
        assertEquals(422, ex.getHttpStatus());
        verify(reviewMapper, never()).updateById(any(PerformanceReview.class));
    }

    @Test
    @DisplayName("人事退回：不能退回自己评的记录")
    void returnForRevisionRejectsSelfReview() {
        loginUser(7L, "ALL", "performance:read", "performance:review");
        PerformanceReview r = review(1L, 11L, 7L, ReviewStatus.SUBMITTED.name());
        when(reviewMapper.selectById(1L)).thenReturn(r);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.returnForRevision(1L, null));
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    @DisplayName("并发创建撞唯一约束时返回 409 而非 500")
    void duplicateReviewMapsToConflict() {
        Employee target = new Employee();
        target.setId(11L);
        when(employeeMapper.selectById(11L)).thenReturn(target);
        ProjectTeam team = new ProjectTeam();
        team.setId(100L);
        team.setManagerEmployeeId(7L);
        when(teamMapper.selectList(any())).thenReturn(List.of(team));
        when(memberMapper.selectCount(any())).thenReturn(1L);
        when(reviewMapper.selectCount(any())).thenReturn(0L);
        doThrow(new org.springframework.dao.DuplicateKeyException("dup")).when(reviewMapper).insert(any(PerformanceReview.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(newRequest(11L, 100L)));
        assertEquals(409, ex.getHttpStatus());
    }
}
