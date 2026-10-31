package com.example.company.dictionary.service;

import com.example.company.dictionary.dto.DictionaryItemRequest;
import com.example.company.dictionary.dto.DictionaryTypeRequest;
import com.example.company.dictionary.entity.DictionaryItem;
import com.example.company.dictionary.entity.DictionaryType;

import java.util.List;

public interface DictionaryService {

    List<DictionaryType> listTypes();

    List<DictionaryItem> listItems(String dictCode);

    List<DictionaryItem> listItemsByTypeId(Long dictTypeId);

    DictionaryType createType(DictionaryTypeRequest request);

    DictionaryType updateType(Long id, DictionaryTypeRequest request);

    void changeTypeStatus(Long id, boolean enabled);

    void deleteType(Long id);

    DictionaryItem createItem(String dictCode, DictionaryItemRequest request);

    DictionaryItem updateItem(Long id, DictionaryItemRequest request);

    void changeItemStatus(Long id, boolean enabled);

    void deleteItem(Long id);

    /**
     * 校验业务字段的字典取值：编码为空视为不填；字典项必须存在且字典类型、字典项都处于启用状态。
     * 供员工档案等写接口调用，避免库里出现 {@code BACHELOR_X} 这类脏值。
     */
    void validateDictionaryItem(String dictCode, String itemCode, String fieldLabel);
}
