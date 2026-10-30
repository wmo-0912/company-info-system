package com.example.company.dictionary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.company.dictionary.entity.DictionaryItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DictionaryItemMapper extends BaseMapper<DictionaryItem> {
}
