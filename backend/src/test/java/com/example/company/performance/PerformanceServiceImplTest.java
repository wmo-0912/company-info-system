package com.example.company.performance;

import com.example.company.audit.service.AuditService;
import com.example.company.common.enums.ReviewStatus;
import com.example.company.common.exception.BusinessException;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
}
