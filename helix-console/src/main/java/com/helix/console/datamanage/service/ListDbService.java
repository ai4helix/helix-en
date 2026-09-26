package com.helix.console.datamanage.service;

import com.helix.console.common.PageResult;
import com.helix.console.datamanage.entity.ListDb;
import com.helix.console.datamanage.entity.ListEntry;

import java.util.List;

public interface ListDbService {

    PageResult<ListDb> page(String listType, Integer status, String keyword, long pageNo, long pageSize);

    ListDb detail(Integer id);

    List<ListDb> listAvailable(String listType);

    List<ListDb> listByIds(List<Integer> ids);

    Integer create(ListDb listDb);

    void update(ListDb listDb);

    Integer copy(Integer id);

    void changeStatus(List<Integer> ids, Integer status);

    void moveToRecycle(List<Integer> ids);

    void restore(List<Integer> ids);

    void removePermanently(List<Integer> ids);


    PageResult<ListEntry> pageEntries(Integer listId, String keyword, Integer status,
                                      long pageNo, long pageSize);

    int addEntries(Integer listId, String valuesText, String remark,
                   java.time.LocalDateTime effectiveFrom, java.time.LocalDateTime effectiveTo);

    void changeEntryStatus(Integer listId, List<Long> entryIds, Integer status);

    void removeEntries(Integer listId, List<Long> entryIds);
}
