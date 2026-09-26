package com.helix.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.helix.engine.entity.FlowPublishEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Publish artifact Mapper.
 */
@Mapper
public interface FlowPublishMapper extends BaseMapper<FlowPublishEntity> {

    /**
     * Take the currently effective publish artifact of a version (status=1, max publish sequence).
     */
    @Select("select * from t_flow_publish where version_id = #{versionId} and status = 1 "
            + "and deleted = 0 order by publish_seq desc limit 1")
    FlowPublishEntity selectActive(@Param("versionId") Integer versionId);

    /**
     * All effective publish artifacts of a version (gray release multi-track coexistence, ordered by publish sequence descending).
     */
    @Select("select * from t_flow_publish where version_id = #{versionId} and status = 1 "
            + "and deleted = 0 order by publish_seq desc")
    java.util.List<FlowPublishEntity> selectActives(@Param("versionId") Integer versionId);

    /** Current max publish sequence of the same version */
    @Select("select ifnull(max(publish_seq), 0) from t_flow_publish "
            + "where version_id = #{versionId} and deleted = 0")
    int maxSeq(@Param("versionId") Integer versionId);
}
