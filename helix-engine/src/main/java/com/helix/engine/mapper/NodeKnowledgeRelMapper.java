package com.helix.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.helix.engine.entity.NodeKnowledgeRel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NodeKnowledgeRelMapper extends BaseMapper<NodeKnowledgeRel> {

    /** Query the knowledge items related to a node */
    default java.util.List<NodeKnowledgeRel> selectByNodeId(Integer nodeId) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NodeKnowledgeRel>()
                .eq(NodeKnowledgeRel::getNodeId, nodeId));
    }
}
