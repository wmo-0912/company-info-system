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

    DictionaryItem createItem(String dictCode, DictionaryItemRequest request);

    DictionaryItem updateItem(Long id, DictionaryItemRequest request);

    void changeItemStatus(Long id, boolean enabled);
}
