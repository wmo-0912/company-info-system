package com.example.company.dictionary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.company.common.exception.BusinessException;
import com.example.company.dictionary.dto.DictionaryItemRequest;
import com.example.company.dictionary.dto.DictionaryTypeRequest;
import com.example.company.dictionary.entity.DictionaryItem;
import com.example.company.dictionary.entity.DictionaryType;
import com.example.company.dictionary.mapper.DictionaryItemMapper;
import com.example.company.dictionary.mapper.DictionaryTypeMapper;
import com.example.company.dictionary.service.DictionaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DictionaryServiceImpl implements DictionaryService {

    private final DictionaryTypeMapper typeMapper;
    private final DictionaryItemMapper itemMapper;

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
        return type;
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
        return item;
    }

    @Override
    @Transactional
    public void changeItemStatus(Long id, boolean enabled) {
        DictionaryItem item = requireItem(id);
        item.setEnabled(enabled);
        itemMapper.updateById(item);
    }

    private DictionaryType requireTypeByCode(String dictCode) {
        DictionaryType type = typeMapper.selectOne(new QueryWrapper<DictionaryType>().eq("dict_code", dictCode));
        if (type == null) {
            throw BusinessException.notFound("字典类型不存在: " + dictCode);
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
