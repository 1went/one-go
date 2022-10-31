package com.onego.entity.dto;

import lombok.Data;

import java.util.List;

/**
 * 滚动分页的结果
 */
@Data
public class ScrollResult {
    /**
     * 数据
     */
    private List<?> list;

    /**
     * 本次查询的最小时间
     */
    private Long minTime;

    /**
     * 相同minTime的个数
     */
    private Integer offset;
}
