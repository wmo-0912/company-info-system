package com.example.company.dictionary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.audit.service.AuditService;
import com.example.company.common.exception.BusinessException;
import com.example.company.dictionary.dto.DictionaryItemRequest;
import com.example.company.dictionary.dto.DictionaryTypeRequest;
import com.example.company.dictionary.entity.DictionaryItem;
import com.example.company.dictionary.entity.DictionaryType;
import com.example.company.dictionary.mapper.DictionaryItemMapper;
import com.example.company.dictionary.mapper.DictionaryTypeMapper;
import com.example.company.dictionary.service.DictionaryService;
import com.example.company.employee.entity.Employee;
import com.example.company.employee.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DictionaryServiceImpl implements DictionaryService {

    /** 被员工档案字段直接引用的内置字典：允许改名/增项/停用，但不允许删除整本字典 */
    private static final Set<String> BUILT_IN_CODES = Set.of("EDUCATION", "POSITION_LEVEL", "GENDER");

    /** 字典编码 → 员工表列名，用于删除字典项前的引用检查 */
    private static final Map<String, String> EMPLOYEE_COLUMNS = Map.of(
            "EDUCATION", "education_code",
            "POSITION_LEVEL", "position_level_code",
            "GENDER", "gender");

    private final DictionaryTypeMapper typeMapper;
    private final DictionaryItemMapper itemMapper;
    private final EmployeeMapper employeeMapper;
    private final AuditService auditService;

    @Override
    public List<DictionaryType> listTypes() {
        return typeMapper.selectList(new QueryWrapper<DictionaryType>().orderByAsc("dict_code"));
    }

    @Override
    public List<DictionaryItem> listItems(String dictCode) {
        DictionaryType type = requireTypeByCode(dictCode);
        return listItemsByTypeId(type.getId());
    }

    @Override
    public List<DictionaryItem> listItemsByTypeId(Long dictTypeId) {
        return itemMapper.selectList(new QueryWrapper<DictionaryItem>()
                .eq("dict_type_id", dictTypeId).orderByAsc("sort_order"));
    }

    @Override
    @Transactional
    public DictionaryType createType(DictionaryTypeRequest request) {
        if (typeMapper.selectCount(new QueryWrapper<DictionaryType>().eq("dict_code", request.getDictCode())) > 0) {
            throw BusinessException.conflict("字典编码已存在: " + request.getDictCode());
        }
        DictionaryType type = new DictionaryType();
        type.setDictCode(request.getDictCode());
        type.setDictName(request.getDictName());
        type.setEnabled(true);
        typeMapper.insert(type);
        auditService.success("dictionary:create", "dictionary_type", type.getId(),
                "创建字典 " + type.getDictCode());
        return type;
    }

    @Override
    @Transactional
    public DictionaryType updateType(Long id, DictionaryTypeRequest request) {
        DictionaryType type = requireType(id);
        if (StringUtils.hasText(request.getDictCode()) && !request.getDictCode().equals(type.getDictCode())) {
            // 编码被业务字段（如 employee.education_code）按约定引用，改名会让历史数据失去含义
            throw BusinessException.badRequest("字典编码创建后不可修改: " + type.getDictCode());
        }
        type.setDictName(request.getDictName());
        typeMapper.updateById(type);
        auditService.success("dictionary:update", "dictionary_type", id,
                "修改字典 " + type.getDictCode() + " 名称为 " + type.getDictName());
        return type;
    }

    @Override
    @Transactional
    public void changeTypeStatus(Long id, boolean enabled) {
        DictionaryType type = requireType(id);
        if (Boolean.TRUE.equals(type.getEnabled()) == enabled) {
            return;
        }
        type.setEnabled(enabled);
        typeMapper.updateById(type);
        auditService.success("dictionary:status", "dictionary_type", id,
                (enabled ? "启用字典 " : "停用字典 ") + type.getDictCode());
    }

    @Override
    @Transactional
    public void deleteType(Long id) {
        DictionaryType type = requireType(id);
        if (BUILT_IN_CODES.contains(type.getDictCode())) {
            throw BusinessException.badRequest("内置字典不允许删除，可改为停用: " + type.getDictCode());
        }
        Long itemCount = itemMapper.selectCount(new QueryWrapper<DictionaryItem>()
                .eq("dict_type_id", id));
        if (itemCount != null && itemCount > 0) {
            throw BusinessException.conflict("字典下还有 " + itemCount + " 个字典项，请先删除字典项");
        }
        typeMapper.deleteById(id);
        auditService.success("dictionary:delete", "dictionary_type", id,
                "删除字典 " + type.getDictCode());
    }

    @Override
    @Transactional
    public DictionaryItem createItem(String dictCode, DictionaryItemRequest request) {
        DictionaryType type = requireTypeByCode(dictCode);
        Long exists = itemMapper.selectCount(new QueryWrapper<DictionaryItem>()
                .eq("dict_type_id", type.getId()).eq("item_code", request.getItemCode()));
        if (exists > 0) {
            throw BusinessException.conflict("字典项编码已存在: " + request.getItemCode());
        }
        DictionaryItem item = new DictionaryItem();
        item.setDictTypeId(type.getId());
        item.setItemCode(request.getItemCode());
        item.setItemLabel(request.getItemLabel());
        item.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        item.setEnabled(true);
        itemMapper.insert(item);
        auditService.success("dictionary:item:create", "dictionary_item", item.getId(),
                "为字典 " + dictCode + " 新增项 " + item.getItemCode());
        return item;
    }

    @Override
    @Transactional
    public DictionaryItem updateItem(Long id, DictionaryItemRequest request) {
        DictionaryItem item = requireItem(id);
        Long conflict = itemMapper.selectCount(new QueryWrapper<DictionaryItem>()
                .eq("dict_type_id", item.getDictTypeId())
                .eq("item_code", request.getItemCode())
                .ne("id", id));
        if (conflict > 0) {
            throw BusinessException.conflict("字典项编码已存在: " + request.getItemCode());
        }
        item.setItemCode(request.getItemCode());
        item.setItemLabel(request.getItemLabel());
        item.setSortOrder(request.getSortOrder() == null ? item.getSortOrder() : request.getSortOrder());
        itemMapper.updateById(item);
        auditService.success("dictionary:item:update", "dictionary_item", id,
                "修改字典项为 " + item.getItemCode() + "（" + item.getItemLabel() + "）");
        return item;
    }

    @Override
    @Transactional
    public void changeItemStatus(Long id, boolean enabled) {
        DictionaryItem item = requireItem(id);
        if (Boolean.TRUE.equals(item.getEnabled()) == enabled) {
            return;
        }
        item.setEnabled(enabled);
        itemMapper.updateById(item);
        auditService.success("dictionary:item:status", "dictionary_item", id,
                (enabled ? "启用字典项 " : "停用字典项 ") + item.getItemCode());
    }

    @Override
    @Transactional
    public void deleteItem(Long id) {
        DictionaryItem item = requireItem(id);
        DictionaryType type = typeMapper.selectById(item.getDictTypeId());
        String column = type == null ? null : EMPLOYEE_COLUMNS.get(type.getDictCode());
        if (column != null) {
            Long used = employeeMapper.selectCount(new QueryWrapper<Employee>()
                    .eq(column, item.getItemCode()));
            if (used != null && used > 0) {
                throw BusinessException.conflict("该字典项已被 " + used + " 名员工使用，不能删除，可改为停用");
            }
        }
        itemMapper.deleteById(id);
        auditService.success("dictionary:item:delete", "dictionary_item", id,
                "删除字典项 " + item.getItemCode());
    }

    @Override
    public void validateDictionaryItem(String dictCode, String itemCode, String fieldLabel) {
        if (!StringUtils.hasText(itemCode)) {
            return;
        }
        DictionaryType type = typeMapper.selectOne(new QueryWrapper<DictionaryType>()
                .eq("dict_code", dictCode).last("limit 1"));
        if (type == null) {
            throw BusinessException.badRequest(fieldLabel + "字典未初始化: " + dictCode);
        }
        if (!Boolean.TRUE.equals(type.getEnabled())) {
            throw BusinessException.badRequest(type.getDictName() + "字典已停用，无法写入 " + itemCode);
        }
        DictionaryItem item = itemMapper.selectOne(new QueryWrapper<DictionaryItem>()
                .eq("dict_type_id", type.getId())
                .eq("item_code", itemCode)
                .last("limit 1"));
        if (item == null) {
            throw BusinessException.badRequest(fieldLabel + "取值不存在: " + itemCode);
        }
        if (!Boolean.TRUE.equals(item.getEnabled())) {
            throw BusinessException.badRequest(fieldLabel + "取值已停用: " + itemCode);
        }
    }

    private DictionaryType requireTypeByCode(String dictCode) {
        DictionaryType type = typeMapper.selectOne(new QueryWrapper<DictionaryType>().eq("dict_code", dictCode));
        if (type == null) {
            throw BusinessException.notFound("字典类型不存在: " + dictCode);
        }
        return type;
    }

    private DictionaryType requireType(Long id) {
        DictionaryType type = typeMapper.selectById(id);
        if (type == null) {
            throw BusinessException.notFound("字典类型不存在");
        }
        return type;
    }

    private DictionaryItem requireItem(Long id) {
        DictionaryItem item = itemMapper.selectById(id);
        if (item == null) {
            throw BusinessException.notFound("字典项不存在");
        }
        return item;
    }
}
