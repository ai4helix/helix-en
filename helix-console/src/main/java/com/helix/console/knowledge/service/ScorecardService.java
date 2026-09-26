package com.helix.console.knowledge.service;

import com.helix.console.common.PageResult;
import com.helix.console.knowledge.dto.ScorecardSaveDTO;
import com.helix.console.knowledge.entity.Scorecard;

import java.util.List;

public interface ScorecardService {

    PageResult<Scorecard> page(Integer parentId, Integer engineId, Integer status, String keyword,
                                long pageNo, long pageSize);

    ScorecardSaveDTO detail(Integer id);

    List<Scorecard> listByIds(List<Integer> ids);

    Integer create(ScorecardSaveDTO dto);

    void update(ScorecardSaveDTO dto);

    Integer copy(Integer id);

    void changeStatus(List<Integer> ids, Integer status);

    void moveToRecycle(List<Integer> ids);

    void restore(List<Integer> ids);

    void removePermanently(List<Integer> ids);
}
