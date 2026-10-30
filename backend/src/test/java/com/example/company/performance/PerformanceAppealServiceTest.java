package com.example.company.performance;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.company.audit.service.AuditService;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.DataScopeService;
import com.example.company.common.security.LoginUser;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import com.example.company.performance.entity.PerformanceAppeal;
import com.example.company.performance.entity.PerformanceReview;
import com.example.company.performance.mapper.PerformanceAppealMapper;
import com.example.company.performance.mapper.PerformanceReviewMapper;
import com.example.company.performance.service.impl.PerformanceAppealServiceImpl;
import com.example.company.performance.vo.PerformanceAppealVO;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceAppealServiceTest {

    @Mock
    private PerformanceAppealMapper appealMapper;
    @Mock
    private PerformanceReviewMapper reviewMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private PerformanceAppealServiceImpl service;

    @BeforeEach
    void setUp() {
        login(5L, "EMPLOYEE");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void login(Long employeeId, String... permissions) {
        LoginUser user = new LoginUser();
        user.setUserId(100L + employeeId);
        user.setUsername("u" + employeeId);
        user.setEmployeeId(employeeId);
        user.setRoles(Set.of("EMPLOYEE"));
        user.setPermissions(Set.of(permissions));
        user.setDataScope("SELF");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @Test
    @DisplayName("只能对本人收到的评价提出申诉")
    void createRejectsOthersReview() {
        when(reviewMapper.selectById(10L)).thenReturn(review(99L, "SUBMITTED"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(10L, "评分不合理"));
        assertEquals(403, ex.getHttpStatus());
        verify(appealMapper, never()).insert(any(PerformanceAppeal.class));
    }

    @Test
    @DisplayName("非已提交状态不能申诉")
    void createRejectsNonSubmittedReview() {
        when(reviewMapper.selectById(10L)).thenReturn(review(5L, "DRAFT"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(10L, "评分不合理"));
        assertEquals(422, ex.getHttpStatus());
    }

    @Test
    @DisplayName("同一条评价不能重复提交待处理申诉")
    void createRejectsDuplicatePendingAppeal() {
        when(reviewMapper.selectById(10L)).thenReturn(review(5L, "SUBMITTED"));
        when(appealMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(10L, "评分不合理"));
        assertEquals(409, ex.getHttpStatus());
        verify(appealMapper, never()).insert(any(PerformanceAppeal.class));
    }

    @Test
    @DisplayName("申诉成功写入待处理状态并记审计")
    void createSavesPendingAppeal() {
        when(reviewMapper.selectById(10L)).thenReturn(review(5L, "SUBMITTED"));
        when(appealMapper.selectCount(any())).thenReturn(0L);
        when(appealMapper.insert(any(PerformanceAppeal.class))).thenAnswer(inv -> {
            PerformanceAppeal a = inv.getArgument(0);
            a.setId(100L);
            return 1;
        });
        when(employeeMapper.selectById(5L)).thenReturn(employee(5L, "张三"));

        PerformanceAppealVO vo = service.create(10L, "  评分明显偏低  ");

        assertEquals("评分明显偏低", vo.getReason());
        assertEquals("PENDING", vo.getStatus());
        assertEquals("2026-Q3", vo.getPeriodKey());
        assertEquals("张三", vo.getEmployeeName());
        verify(auditService).success(any(), any(), any(), any());
    }

    @Test
    @DisplayName("受理申诉会把评价退回评价人复核，不直接改分")
    void handleAcceptsAndReturnsReview() {
        login(5L); // 处理人是本人 → 换一个处理人
        login(9L);
        PerformanceAppeal appeal = appeal(7L, 10L, 5L, "PENDING");
        when(appealMapper.selectById(7L)).thenReturn(appeal);
        PerformanceReview review = review(5L, "SUBMITTED");
        when(reviewMapper.selectById(10L)).thenReturn(review);
        when(employeeMapper.selectById(5L)).thenReturn(employee(5L, "张三"));
        when(employeeMapper.selectById(9L)).thenReturn(employee(9L, "李四"));

        PerformanceAppealVO vo = service.handle(7L, "ACCEPT", "已核实，退回重新评分");

        assertEquals("ACCEPTED", vo.getStatus());
        assertEquals(9L, vo.getHandlerEmployeeId());
        assertEquals("李四", vo.getHandlerName());
        assertNotNull(vo.getHandledAt());
        assertEquals("RETURNED", review.getStatus());
        verify(reviewMapper).updateById(review);
        // 分数不被直接修改
        assertEquals(0, new BigDecimal("88").compareTo(review.getScore()));
    }

    @Test
    @DisplayName("驳回必须填写处理意见")
    void handleRejectRequiresComment() {
        login(9L); // 处理人不能是申诉人本人
        when(appealMapper.selectById(7L)).thenReturn(appeal(7L, 10L, 5L, "PENDING"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.handle(7L, "REJECT", "  "));
        assertEquals(400, ex.getHttpStatus());
        verify(appealMapper, never()).updateById(any(PerformanceAppeal.class));
    }

    @Test
    @DisplayName("不能处理自己提出的申诉")
    void handleCannotHandleOwnAppeal() {
        when(appealMapper.selectById(7L)).thenReturn(appeal(7L, 10L, 5L, "PENDING"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.handle(7L, "ACCEPT", "同意"));
        assertEquals(403, ex.getHttpStatus());
    }

    @Test
    @DisplayName("数据范围内没有员工时申诉列表返回空页且不查库")
    void pageReturnsEmptyWhenScopeEmpty() {
        when(dataScopeService.visibleEmployeeIds(any(), any())).thenReturn(Set.of());

        var res = service.page(1, 10, "PENDING");

        assertTrue(res.getRecords().isEmpty());
        assertEquals(0, res.getTotal());
        verify(appealMapper, never()).selectPage(any(), any());
    }

    private PerformanceReview review(Long employeeId, String status) {
        PerformanceReview r = new PerformanceReview();
        r.setId(10L);
        r.setEmployeeId(employeeId);
        r.setReviewerEmployeeId(9L);
        r.setPeriodType("QUARTERLY");
        r.setPeriodKey("2026-Q3");
        r.setScore(new BigDecimal("88"));
        r.setStatus(status);
        return r;
    }

    private PerformanceAppeal appeal(Long id, Long reviewId, Long employeeId, String status) {
        PerformanceAppeal a = new PerformanceAppeal();
        a.setId(id);
        a.setReviewId(reviewId);
        a.setEmployeeId(employeeId);
        a.setReason("评分偏低");
        a.setStatus(status);
        return a;
    }

    private Employee employee(Long id, String name) {
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeName(name);
        return e;
    }
}
