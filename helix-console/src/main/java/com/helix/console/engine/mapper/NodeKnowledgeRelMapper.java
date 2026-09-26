package com.helix.console.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.helix.console.engine.entity.NodeKnowledgeRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NodeKnowledgeRelMapper extends BaseMapper<NodeKnowledgeRel> {

    @Select("select * from t_node_knowledge_rel where node_id = #{nodeId}")
    List<NodeKnowledgeRel> selectByNodeId(@Param("nodeId") Integer nodeId);

    @Select("<script>select * from t_node_knowledge_rel where node_id in "
            + "<foreach collection='nodeIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<NodeKnowledgeRel> selectByNodeIds(@Param("nodeIds") List<Integer> nodeIds);
}
