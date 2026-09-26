package com.helix.console.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.helix.console.engine.entity.EngineNode;
import com.helix.console.engine.entity.NodeRefRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EngineNodeMapper extends BaseMapper<EngineNode> {

    @Select("<script>select n.node_id as nodeId, n.node_code as nodeCode, n.node_name as nodeName, "
            + "v.id as versionId, v.version as version, v.sub_version as subVersion, "
            + "e.id as engineId, e.code as engineCode, e.name as engineName "
            + "from t_engine_node n "
            + "join t_engine_version v on v.id = n.version_id "
            + "join t_engine e on e.id = v.engine_id "
            + "where n.node_id in "
            + "<foreach collection='nodeIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<NodeRefRow> selectNodeRefsByNodeIds(@Param("nodeIds") List<Integer> nodeIds);

    @Select("select n.node_id as nodeId, n.node_code as nodeCode, n.node_name as nodeName, "
            + "v.id as versionId, v.version as version, v.sub_version as subVersion, "
            + "e.id as engineId, e.code as engineCode, e.name as engineName "
            + "from t_engine_node n "
            + "join t_engine_version v on v.id = n.version_id "
            + "join t_engine e on e.id = v.engine_id "
            + "where n.node_json is not null and n.node_json <> '' "
            + "and n.node_json->>'$.decision_table_id' = cast(#{dtId} as char)")
    List<NodeRefRow> selectNodeRefsByDecisionTable(@Param("dtId") Integer dtId);

    @Select("select n.node_id as nodeId, n.node_code as nodeCode, n.node_name as nodeName, "
            + "v.id as versionId, v.version as version, v.sub_version as subVersion, "
            + "e.id as engineId, e.code as engineCode, e.name as engineName "
            + "from t_engine_node n "
            + "join t_engine_version v on v.id = n.version_id "
            + "join t_engine e on e.id = v.engine_id "
            + "where n.node_json is not null and n.node_json <> '' "
            + "and (json_contains(n.node_json->'$.list_db_ids', cast(#{listDbId} as json)) "
            + "     or find_in_set(#{listDbId}, replace(replace("
            + "        n.node_json->>'$.list_db_id', ' ', ''), '\"', '')))")
    List<NodeRefRow> selectNodeRefsByListDb(@Param("listDbId") Integer listDbId);

    @Select("select n.node_id as nodeId, n.node_code as nodeCode, n.node_name as nodeName, "
            + "v.id as versionId, v.version as version, v.sub_version as subVersion, "
            + "e.id as engineId, e.code as engineCode, e.name as engineName "
            + "from t_engine_node n "
            + "join t_engine_version v on v.id = n.version_id "
            + "join t_engine e on e.id = v.engine_id "
            + "where n.node_json is not null and n.node_json <> '' "
            + "and n.node_json->>'$.cardId' = cast(#{cardId} as char)")
    List<NodeRefRow> selectNodeRefsByScorecard(@Param("cardId") Integer cardId);

    @Select("select * from t_engine_node where version_id = #{versionId} order by node_order, node_id")
    List<EngineNode> selectByVersionId(@Param("versionId") Integer versionId);

    @Select("select * from t_engine_node where version_id = #{versionId} and find_in_set(#{nodeCode}, next_nodes)")
    List<EngineNode> selectByNextNodeCode(@Param("versionId") Integer versionId,
                                          @Param("nodeCode") String nodeCode);

    @Select("select distinct e.code as engineCode "
            + "from t_engine_node n "
            + "join t_engine_version v on v.id = n.version_id "
            + "join t_engine e on e.id = v.engine_id "
            + "where n.node_json is not null and n.node_json <> '' "
            + "and (json_contains(n.node_json->'$.list_db_ids', cast(#{listDbId} as json)) "
            + "     or find_in_set(#{listDbId}, replace(replace("
            + "        n.node_json->>'$.list_db_id', ' ', ''), '\"', '')))")
    List<String> selectEngineCodesByListDb(@Param("listDbId") Integer listDbId);
}
