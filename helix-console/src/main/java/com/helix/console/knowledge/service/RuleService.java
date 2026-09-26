package com.helix.console.knowledge.service;

import com.helix.console.common.PageResult;
import com.helix.console.knowledge.dto.RuleConditionNodeDTO;
import com.helix.console.knowledge.dto.RuleSaveDTO;
import com.helix.console.knowledge.dto.RuleVO;

import java.util.List;

public interface RuleService {

    PageResult<RuleVO> page(Integer parentId, Integer engineId, Integer status, String keyword,
                            long pageNo, long pageSize);

    RuleVO detail(Integer id);

    List<RuleConditionNodeDTO> getRuleAst(Integer id);

    java.util.List<com.helix.console.knowledge.dto.RuleBriefVO> listBriefs(java.util.List<Integer> ids);

    List<RuleVO> listByIds(List<Integer> ids);

    Integer create(RuleSaveDTO dto);

    void update(RuleSaveDTO dto);

    Integer copy(Integer id);

    java.util.Map<String, Object> dryRun(com.helix.console.knowledge.dto.DryRunReq req);

    void changeStatus(List<Integer> ids, Integer status);

    void moveToRecycle(List<Integer> ids);

    void moveCatalog(Integer id, Integer parentId);

    void restore(List<Integer> ids);

    void removePermanently(List<Integer> ids);
}
