package com.helix.console.knowledge.service;

import com.helix.console.knowledge.dto.TreeNodeVO;

import java.util.List;

public interface KnowledgeTreeService {

    List<TreeNodeVO> tree(Integer treeType, Integer engineId);

    Integer create(String name, Integer parentId, Integer treeType, Integer engineId, Integer organId);

    void rename(Integer id, String name);

    void remove(Integer id);

    void move(Integer id, Integer newParentId);
}
