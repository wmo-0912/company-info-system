package com.example.company.performance.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 绩效汇总（按周期）：只统计已提交（SUBMITTED）的评价，草稿与被退回的记录不计入。
 */
@Data
public class PerformanceSummaryVO {

    private String periodType;
    private String periodKey;
    /** 纳入统计的评价条数 */
    private int reviewCount;
    /** 被评价人数（同一员工多个评价人只算一人） */
    private int employeeCount;
    /** 平均分，无有效评分时为 null */
    private BigDecimal averageScore;
    private BigDecimal minScore;
    private BigDecimal maxScore;
    /** 分数段分布，固定五档，便于前端直接画柱状图 */
    private List<BucketVO> scoreBuckets = new ArrayList<>();
    /** 按部门聚合 */
    private List<GroupVO> departments = new ArrayList<>();
    /** 按项目组聚合（未关联项目组的评价不计入此维度） */
    private List<GroupVO> projects = new ArrayList<>();

    @Data
    public static class BucketVO {
        private String label;
        private long count;

        public BucketVO() {
        }

        public BucketVO(String label, long count) {
            this.label = label;
            this.count = count;
        }
    }

    @Data
    public static class GroupVO {
        private Long id;
        private String name;
        private long reviewCount;
        private long employeeCount;
        private BigDecimal averageScore;
    }
}
