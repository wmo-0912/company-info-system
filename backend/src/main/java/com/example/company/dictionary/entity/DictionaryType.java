package com.example.company.dictionary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_dictionary_type")
public class DictionaryType {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String dictCode;

    private String dictName;

    private Boolean enabled;
}
