package com.helix.console.datamanage.service;

import com.helix.console.common.PageResult;
import com.helix.console.datamanage.dto.FieldSaveDTO;
import com.helix.console.datamanage.dto.FieldVO;

import java.util.List;

public interface FieldService {

    PageResult<FieldVO> page(String keyword, Integer fieldTypeId, Integer isOutput,
                             Integer catalogId, Boolean recycle, long pageNo, long pageSize);

    List<FieldVO> listAll();

    List<FieldVO> listByIds(List<Integer> ids);

    List<java.util.Map<String, Object>> listFieldTypes();

    FieldVO create(FieldSaveDTO dto);

    FieldVO update(Integer id, FieldSaveDTO dto);

    void delete(Integer id);

    void moveCatalog(Integer id, Integer catalogId);


    void recycle(List<Integer> ids);

    void restore(List<Integer> ids);

    void deletePermanently(List<Integer> ids);
}
