package com.example.company.performance;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.audit.service.AuditService;
import com.example.company.common.security.DataScopeService;
import com.example.company.common.security.LoginUser;
import com.example.company.department.entity.Department;
import com.example.company.department.mapper.DepartmentMapper;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.performance.entity.PerformanceReview;
import com.example.company.performance.mapper.PerformanceReviewMapper;
import com.example.company.performance.service.PerformancePeriodService;
import com.example.company.performance.service.impl.PerformanceServiceImpl;
import com.example.company.performance.vo.PerformanceSummaryVO;
import com.example.company.project.entity.ProjectTeam;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceSummaryTest {

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
    @Mock
    private PerformancePeriodService periodService;
    @Mock
    private DepartmentMapper departmentMapper;

    @InjectMocks
    private PerformanceServiceImpl service;

    @BeforeEach
    void setUp() {
        LoginUser hr = new LoginUser();
        hr.setUserId(2L);
        hr.setUsername("hr");
        hr.setEmployeeId(1L);
        hr.setRoles(Set.of("HR"));
        hr.setPermissions(Set.of("performance:read", "performance:review"));
        hr.setDataScope("ALL");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(hr, null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("汇总按部门与项目组聚合平均分和分数段")
    void summaryAggregatesByDepartmentAndProject() {
        when(dataScopeService.visibleEmployeeIds(any(), any())).thenReturn(null);
        when(reviewMapper.selectList(any())).thenReturn(List.of(
                review(1L, 95, 10L, "2026-Q3"),
                review(1L, 70, 10L, "2026-Q3"),
                review(2L, 55, null, "2026-Q3"),
                review(3L, 89, 20L, "2026-Q3")));
        when(employeeMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                employee(1L, "E001", 100L),
                employee(2L, "E002", 100L),
                employee(3L, "E003", 200L)));
        when(departmentMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                department(100L, "研发部"),
                department(200L, "市场部")));
        when(teamMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                team(10L, "项目A"),
                team(20L, "项目B")));

        PerformanceSummaryVO vo = service.summary("QUARTERLY", "2026-Q3");

        assertEquals("2026-Q3", vo.getPeriodKey());
        assertEquals(4, vo.getReviewCount());
        assertEquals(3, vo.getEmployeeCount());
        assertEquals(0, new BigDecimal("77.25").compareTo(vo.getAverageScore()));
        assertEquals(0, new BigDecimal("55").compareTo(vo.getMinScore()));
        assertEquals(0, new BigDecimal("95").compareTo(vo.getMaxScore()));
        assertEquals(5, vo.getScoreBuckets().size());
        assertEquals(1, bucket(vo, "90-100"));
        assertEquals(1, bucket(vo, "80-89"));
        assertEquals(1, bucket(vo, "70-79"));
        assertEquals(0, bucket(vo, "60-69"));
        assertEquals(1, bucket(vo, "60 以下"));

        assertEquals(2, vo.getDepartments().size());
        PerformanceSummaryVO.GroupVO dev = vo.getDepartments().stream()
                .filter(g -> Long.valueOf(100L).equals(g.getId())).findFirst().orElseThrow();
        assertEquals("研发部", dev.getName());
        assertEquals(3, dev.getReviewCount());
        assertEquals(2, dev.getEmployeeCount());
        assertEquals(0, new BigDecimal("73.33").compareTo(dev.getAverageScore()));
        PerformanceSummaryVO.GroupVO market = vo.getDepartments().stream()
                .filter(g -> Long.valueOf(200L).equals(g.getId())).findFirst().orElseThrow();
        assertEquals(1, market.getReviewCount());

        // 两个项目组 + 一个「未关联项目组」桶
        assertEquals(3, vo.getProjects().size());
        PerformanceSummaryVO.GroupVO projectA = vo.getProjects().stream()
                .filter(g -> Long.valueOf(10L).equals(g.getId())).findFirst().orElseThrow();
        assertEquals("项目A", projectA.getName());
        assertEquals(2, projectA.getReviewCount());
        assertEquals(0, new BigDecimal("82.50").compareTo(projectA.getAverageScore()));
        assertTrue(vo.getProjects().stream().anyMatch(g -> g.getId() == null && "未关联项目组".equals(g.getName())));
    }

    @Test
    @DisplayName("数据范围内没有员工时返回全零且不查明细")
    void summaryReturnsZerosWhenScopeEmpty() {
        when(dataScopeService.visibleEmployeeIds(any(), any())).thenReturn(Set.of());

        PerformanceSummaryVO vo = service.summary(null, null);

        assertEquals(0, vo.getReviewCount());
        assertEquals(0, vo.getEmployeeCount());
        assertNull(vo.getAverageScore());
        assertEquals(5, vo.getScoreBuckets().size());
        vo.getScoreBuckets().forEach(b -> assertEquals(0, b.getCount()));
        verify(reviewMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("只统计已提交评价并按周期过滤")
    void summaryOnlyCountsSubmittedReviewsInPeriod() {
        when(dataScopeService.visibleEmployeeIds(any(), any())).thenReturn(null);
        when(reviewMapper.selectList(any())).thenReturn(List.of());

        service.summary("MONTHLY", "2026-09");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<PerformanceReview>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(reviewMapper).selectList(captor.capture());
        QueryWrapper<PerformanceReview> qw = captor.getValue();
        assertTrue(qw.getSqlSegment().contains("status"));
        assertTrue(qw.getParamNameValuePairs().containsValue("SUBMITTED"));
        assertTrue(qw.getParamNameValuePairs().containsValue("MONTHLY"));
        assertTrue(qw.getParamNameValuePairs().containsValue("2026-09"));
    }

    @Test
    @DisplayName("没有评分的记录只计入条数，不参与平均分")
    void summaryIgnoresReviewsWithoutScore() {
        when(dataScopeService.visibleEmployeeIds(any(), any())).thenReturn(Set.of(1L, 2L));
        when(reviewMapper.selectList(any())).thenReturn(List.of(
                review(1L, 80, null, "2026-Q3"),
                review(2L, null, null, "2026-Q3")));
        when(employeeMapper.selectBatchIds(anyCollection())).thenReturn(List.of(employee(1L, "E001", 100L)));
        when(departmentMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

        PerformanceSummaryVO vo = service.summary(null, "2026-Q3");

        assertEquals(2, vo.getReviewCount());
        assertEquals(2, vo.getEmployeeCount());
        assertEquals(0, new BigDecimal("80").compareTo(vo.getAverageScore()));
        assertEquals(0, new BigDecimal("80").compareTo(vo.getMinScore()));
        assertEquals(0, new BigDecimal("80").compareTo(vo.getMaxScore()));
        assertEquals(1, bucket(vo, "80-89"));
        assertEquals("未知部门", vo.getDepartments().get(0).getName());
    }

    private long bucket(PerformanceSummaryVO vo, String label) {
        return vo.getScoreBuckets().stream().filter(b -> label.equals(b.getLabel()))
                .findFirst().orElseThrow().getCount();
    }

    private PerformanceReview review(Long employeeId, Integer score, Long teamId, String periodKey) {
        PerformanceReview r = new PerformanceReview();
        r.setEmployeeId(employeeId);
        r.setReviewerEmployeeId(9L);
        r.setProjectTeamId(teamId);
        r.setPeriodType("QUARTERLY");
        r.setPeriodKey(periodKey);
        r.setScore(score == null ? null : BigDecimal.valueOf(score));
        r.setStatus("SUBMITTED");
        return r;
    }

    private Employee employee(Long id, String no, Long departmentId) {
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeNo(no);
        e.setDepartmentId(departmentId);
        return e;
    }

    private Department department(Long id, String name) {
        Department d = new Department();
        d.setId(id);
        d.setDepartmentName(name);
        return d;
    }

    private ProjectTeam team(Long id, String name) {
        ProjectTeam t = new ProjectTeam();
        t.setId(id);
        t.setProjectName(name);
        return t;
    }
}
