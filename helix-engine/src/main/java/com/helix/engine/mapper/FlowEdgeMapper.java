package com.helix.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.helix.engine.entity.FlowEdgeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Decision Flow edge Mapper (engine read-only: collected into the artifact and frozen at publish time,
 * zero DB queries on the decision path).
 */
@Mapper
public interface FlowEdgeMapper extends BaseMapper<FlowEdgeEntity> {

    /** All valid edges within the version (ordered by priority ascending, stable traversal order for multi-edges from one source) */
    @Select("select * from t_flow_edge where version_id = #{versionId} and deleted = 0 "
            + "order by from_code, priority, id")
    List<FlowEdgeEntity> selectByVersionId(@Param("versionId") Integer versionId);
}
