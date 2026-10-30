package com.example.company.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;
import java.util.function.Function;

@Data
public class PageResponse<T> {

    private List<T> records;
    private long page;
    private long pageSize;
    private long total;

    public static <E, T> PageResponse<T> from(IPage<E> page, Function<E, T> mapper) {
        PageResponse<T> r = new PageResponse<>();
        r.setRecords(page.getRecords().stream().map(mapper).toList());
        r.setPage(page.getCurrent());
        r.setPageSize(page.getSize());
        r.setTotal(page.getTotal());
        return r;
    }

    public static <T> PageResponse<T> of(List<T> records, long page, long pageSize, long total) {
        PageResponse<T> r = new PageResponse<>();
        r.setRecords(records);
        r.setPage(page);
        r.setPageSize(pageSize);
        r.setTotal(total);
        return r;
    }
}
