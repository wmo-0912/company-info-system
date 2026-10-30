package com.example.company.dictionary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_dictionary_item")
public class DictionaryItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dictTypeId;

    private String itemCode;

    private String itemLabel;

    private Integer sortOrder;

    private Boolean enabled;
}
