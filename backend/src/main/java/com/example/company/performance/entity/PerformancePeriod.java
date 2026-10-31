package com.example.company.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** 绩效周期：只记录被显式锁定过的周期，未记录的周期一律视为未锁定 */
@Data
@TableName("performance_period")
public class PerformancePeriod {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String periodKey;
    private Boolean locked;
    private OffsetDateTime lockedAt;
    private Long lockedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
