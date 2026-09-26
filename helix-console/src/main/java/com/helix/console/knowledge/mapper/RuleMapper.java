package com.helix.console.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.helix.console.knowledge.entity.Rule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RuleMapper extends BaseMapper<Rule> {

    @Select("<script>select * from t_rule where status = 1 and id in "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " order by priority, id</script>")
    List<Rule> selectEnabledByIds(@Param("ids") List<Integer> ids);
}
