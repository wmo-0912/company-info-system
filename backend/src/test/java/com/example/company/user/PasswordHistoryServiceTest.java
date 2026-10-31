package com.example.company.user;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.common.exception.BusinessException;
import com.example.company.user.entity.SysPasswordHistory;
import com.example.company.user.mapper.SysPasswordHistoryMapper;
import com.example.company.user.service.PasswordHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordHistoryServiceTest {

    @Mock
    private SysPasswordHistoryMapper passwordHistoryMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordHistoryService service;

    private SysPasswordHistory history(long id, String hash) {
        SysPasswordHistory h = new SysPasswordHistory();
        h.setId(id);
        h.setUserId(9L);
        h.setPasswordHash(hash);
        return h;
    }

    private List<SysPasswordHistory> histories(int count) {
        List<SysPasswordHistory> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(history(i, "hash-" + i));
        }
        return list;
    }

    @Test
    void rejectsPasswordMatchingRecentHash() {
        when(passwordHistoryMapper.selectList(any())).thenReturn(histories(3));
        when(passwordEncoder.matches("OldPass123", "hash-1")).thenReturn(false);
        when(passwordEncoder.matches("OldPass123", "hash-2")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertNotReused(9L, "OldPass123"));

        assertEquals(400, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("最近 5 次"), ex.getMessage());
    }

    @Test
    void allowsPasswordAbsentFromHistory() {
        when(passwordHistoryMapper.selectList(any())).thenReturn(histories(2));
        when(passwordEncoder.matches(eq("BrandNew123"), anyString())).thenReturn(false);

        assertDoesNotThrow(() -> service.assertNotReused(9L, "BrandNew123"));
    }

    @Test
    void emptyHistoryNeverRejects() {
        when(passwordHistoryMapper.selectList(any())).thenReturn(List.of());

        assertDoesNotThrow(() -> service.assertNotReused(9L, "Anything123"));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void recordInsertsThenPrunesToDepth() {
        when(passwordHistoryMapper.selectList(any())).thenReturn(histories(5));

        service.record(9L, "new-hash");

        ArgumentCaptor<SysPasswordHistory> insert = ArgumentCaptor.forClass(SysPasswordHistory.class);
        verify(passwordHistoryMapper).insert(insert.capture());
        assertEquals(9L, insert.getValue().getUserId());
        assertEquals("new-hash", insert.getValue().getPasswordHash());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SysPasswordHistory>> delete = ArgumentCaptor.forClass(Wrapper.class);
        verify(passwordHistoryMapper).delete(delete.capture());
        assertTrue(delete.getValue() instanceof QueryWrapper);
        QueryWrapper<?> wrapper = (QueryWrapper<?>) delete.getValue();
        assertTrue(wrapper.getSqlSegment().contains("NOT IN"), wrapper.getSqlSegment());
        assertTrue(wrapper.getSqlSegment().contains("user_id"), wrapper.getSqlSegment());
        // 参数为 1 个 user_id + 保留的 5 个主键
        assertEquals(6, wrapper.getParamNameValuePairs().size(), "保留最近 5 条主键");
        assertTrue(wrapper.getParamNameValuePairs().containsValue(9L));
    }

    @Test
    void recordKeepsAllWhenBelowDepth() {
        when(passwordHistoryMapper.selectList(any())).thenReturn(histories(2));

        service.record(9L, "new-hash");

        verify(passwordHistoryMapper).insert(any(SysPasswordHistory.class));
        verify(passwordHistoryMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void nullArgumentsAreIgnored() {
        service.record(null, "hash");
        service.record(9L, null);
        service.assertNotReused(9L, null);

        verify(passwordHistoryMapper, never()).insert(any(SysPasswordHistory.class));
        verify(passwordHistoryMapper, never()).selectList(any());
    }
}
