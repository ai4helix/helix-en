package com.helix.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.helix.engine.entity.RuleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Rule table Mapper.
 */
@Mapper
public interface RuleMapper extends BaseMapper<RuleEntity> {

    /**
     * Batch-fetch enabled rules by ids, for compiling condition ASTs at load time.
     */
    @Select("<script>select * from t_rule where status = 1 and id in "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " order by priority, id</script>")
    List<RuleEntity> selectEnabledByIds(@Param("ids") List<Integer> ids);
}
