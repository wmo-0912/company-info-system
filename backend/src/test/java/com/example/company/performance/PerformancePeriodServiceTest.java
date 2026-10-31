package com.example.company.performance;

import com.example.company.audit.service.AuditService;
import com.example.company.common.exception.BusinessException;
import com.example.company.common.security.LoginUser;
import com.example.company.performance.entity.PerformancePeriod;
import com.example.company.performance.mapper.PerformancePeriodMapper;
import com.example.company.performance.service.PerformancePeriodService;
import com.example.company.performance.vo.PerformancePeriodVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerformancePeriodServiceTest {

    @Mock
    private PerformancePeriodMapper periodMapper;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private PerformancePeriodService service;

    private void loginAs(Long userId) {
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setUsername("hr");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unknownPeriodIsNotLocked() {
        when(periodMapper.selectOne(any())).thenReturn(null);

        assertFalse(service.isLocked("2026-09"));
        assertFalse(service.isLocked(null));
    }

    @Test
    void lockingNewPeriodInsertsRowAndAudits() {
        loginAs(7L);
        when(periodMapper.selectOne(any())).thenReturn(null);

        PerformancePeriodVO vo = service.setLocked(" 2026-Q3 ", true);

        assertEquals("2026-Q3", vo.periodKey());
        assertTrue(vo.locked());
        assertEquals(7L, vo.lockedBy());
        ArgumentCaptor<PerformancePeriod> captor = ArgumentCaptor.forClass(PerformancePeriod.class);
        verify(periodMapper).insert(captor.capture());
        assertTrue(captor.getValue().getLocked());
        assertNotNull(captor.getValue().getLockedAt());
        verify(auditService).success(eq("performance:period-lock"), eq("performance_period"), eq("2026-Q3"), any());
    }

    @Test
    void unlockingExistingPeriodUpdatesAndClearsStamp() {
        LoginUser user = new LoginUser();
        user.setUserId(7L);
        PerformancePeriod existing = new PerformancePeriod();
        existing.setId(1L);
        existing.setPeriodKey("2026-Q3");
        existing.setLocked(true);
        when(periodMapper.selectOne(any())).thenReturn(existing);

        PerformancePeriodVO vo = service.setLocked("2026-Q3", false);

        assertFalse(vo.locked());
        assertNull(vo.lockedAt());
        assertNull(vo.lockedBy());
        verify(periodMapper).updateById(existing);
        verify(auditService).success(eq("performance:period-unlock"), eq("performance_period"), eq("2026-Q3"), any());
    }

    @Test
    void settingSameStateSkipsWrite() {
        PerformancePeriod existing = new PerformancePeriod();
        existing.setPeriodKey("2026-Q3");
        existing.setLocked(true);
        when(periodMapper.selectOne(any())).thenReturn(existing);

        service.setLocked("2026-Q3", true);

        verify(periodMapper, never()).updateById(any(PerformancePeriod.class));
        verify(periodMapper, never()).insert(any(PerformancePeriod.class));
        verify(auditService, never()).success(any(), any(), any(), any());
    }

    @Test
    void blankOrOverlongKeyRejected() {
        BusinessException blank = assertThrows(BusinessException.class, () -> service.setLocked("  ", true));
        assertEquals(400, blank.getHttpStatus());
        BusinessException tooLong = assertThrows(BusinessException.class,
                () -> service.setLocked("2026-09-01-EXTRA-LONG", true));
        assertEquals(400, tooLong.getHttpStatus());
        verify(periodMapper, never()).insert(any(PerformancePeriod.class));
    }
}
