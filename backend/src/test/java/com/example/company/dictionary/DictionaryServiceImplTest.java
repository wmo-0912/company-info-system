package com.example.company.dictionary;

import com.example.company.audit.service.AuditService;
import com.example.company.common.exception.BusinessException;
import com.example.company.dictionary.dto.DictionaryItemRequest;
import com.example.company.dictionary.dto.DictionaryTypeRequest;
import com.example.company.dictionary.entity.DictionaryItem;
import com.example.company.dictionary.entity.DictionaryType;
import com.example.company.dictionary.mapper.DictionaryItemMapper;
import com.example.company.dictionary.mapper.DictionaryTypeMapper;
import com.example.company.dictionary.service.impl.DictionaryServiceImpl;
import com.example.company.employee.mapper.EmployeeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DictionaryServiceImplTest {

    @Mock
    private DictionaryTypeMapper typeMapper;
    @Mock
    private DictionaryItemMapper itemMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private DictionaryServiceImpl service;

    private DictionaryType type(Long id, String code, boolean enabled) {
        DictionaryType t = new DictionaryType();
        t.setId(id);
        t.setDictCode(code);
        t.setDictName(code + "-名称");
        t.setEnabled(enabled);
        return t;
    }

    private DictionaryItem item(Long id, Long typeId, String code, boolean enabled) {
        DictionaryItem i = new DictionaryItem();
        i.setId(id);
        i.setDictTypeId(typeId);
        i.setItemCode(code);
        i.setItemLabel(code);
        i.setEnabled(enabled);
        return i;
    }

    @Test
    @DisplayName("写入未定义的字典项编码时返回 400，而不是静默落库")
    void validateRejectsUnknownItem() {
        when(typeMapper.selectOne(any())).thenReturn(type(1L, "EDUCATION", true));
        when(itemMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateDictionaryItem("EDUCATION", "BACHELOR_X", "学历"));

        assertEquals(400, ex.getHttpStatus());
        assertEquals(true, ex.getMessage().contains("取值不存在"));
    }

    @Test
    @DisplayName("字典项被停用后不能再写入")
    void validateRejectsDisabledItem() {
        when(typeMapper.selectOne(any())).thenReturn(type(1L, "EDUCATION", true));
        when(itemMapper.selectOne(any())).thenReturn(item(2L, 1L, "BACHELOR", false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateDictionaryItem("EDUCATION", "BACHELOR", "学历"));

        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    @DisplayName("不填字典字段时不查库")
    void validateSkipsBlankValue() {
        assertDoesNotThrow(() -> service.validateDictionaryItem("EDUCATION", "  ", "学历"));
        verifyNoInteractions(typeMapper, itemMapper);
    }

    @Test
    @DisplayName("内置字典不允许删除")
    void deleteBuiltInTypeRejected() {
        when(typeMapper.selectById(1L)).thenReturn(type(1L, "POSITION_LEVEL", true));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteType(1L));

        assertEquals(400, ex.getHttpStatus());
        verify(typeMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("字典下仍有字典项时拒绝删除字典")
    void deleteTypeWithItemsRejected() {
        when(typeMapper.selectById(9L)).thenReturn(type(9L, "CONTRACT_TYPE", true));
        when(itemMapper.selectCount(any())).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteType(9L));

        assertEquals(409, ex.getHttpStatus());
        verify(typeMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("字典项已被员工引用时拒绝删除，提示改为停用")
    void deleteItemInUseRejected() {
        when(itemMapper.selectById(5L)).thenReturn(item(5L, 1L, "BACHELOR", true));
        when(typeMapper.selectById(1L)).thenReturn(type(1L, "EDUCATION", true));
        when(employeeMapper.selectCount(any())).thenReturn(3L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteItem(5L));

        assertEquals(409, ex.getHttpStatus());
        assertEquals(true, ex.getMessage().contains("3"));
        verify(itemMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("字典编码创建后不可修改")
    void updateTypeRejectsCodeChange() {
        when(typeMapper.selectById(1L)).thenReturn(type(1L, "EDUCATION", true));
        DictionaryTypeRequest request = new DictionaryTypeRequest();
        request.setDictCode("EDU");
        request.setDictName("学历");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateType(1L, request));

        assertEquals(400, ex.getHttpStatus());
        verify(typeMapper, never()).updateById(any(DictionaryType.class));
    }

    @Test
    @DisplayName("状态未变化时不写库")
    void changeTypeStatusIsIdempotent() {
        when(typeMapper.selectById(1L)).thenReturn(type(1L, "EDUCATION", true));

        service.changeTypeStatus(1L, true);

        verify(typeMapper, never()).updateById(any(DictionaryType.class));
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("新增字典项时写审计")
    void createItemWritesAudit() {
        when(typeMapper.selectOne(any())).thenReturn(type(1L, "EDUCATION", true));
        when(itemMapper.selectCount(any())).thenReturn(0L);
        DictionaryItemRequest request = new DictionaryItemRequest();
        request.setItemCode("PHD");
        request.setItemLabel("博士");

        service.createItem("EDUCATION", request);

        verify(itemMapper).insert(any(DictionaryItem.class));
        verify(auditService).success(any(), any(), any(), any());
    }
}
