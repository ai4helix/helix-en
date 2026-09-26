package com.helix.console.common;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> records = new ArrayList<>();

    private long total;

    private long pageNo;

    private long pageSize;

    private long pages;

    public static <T> PageResult<T> of(List<T> records, long total, long pageNo, long pageSize) {
        PageResult<T> r = new PageResult<>();
        r.setRecords(records == null ? new ArrayList<>() : records);
        r.setTotal(total);
        r.setPageNo(pageNo);
        r.setPageSize(pageSize);
        r.setPages(pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize);
        return r;
    }
}
