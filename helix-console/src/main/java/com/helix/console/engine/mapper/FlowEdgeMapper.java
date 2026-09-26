package com.helix.console.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.helix.console.engine.entity.FlowEdgeEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FlowEdgeMapper extends BaseMapper<FlowEdgeEntity> {

    @Delete("delete from t_flow_edge where version_id = #{versionId}")
    int deleteByVersionId(@Param("versionId") Integer versionId);

    @Select("select * from t_flow_edge where version_id = #{versionId} and deleted = 0 "
            + "order by from_code, priority, id")
    List<FlowEdgeEntity> selectByVersionId(@Param("versionId") Integer versionId);
}
