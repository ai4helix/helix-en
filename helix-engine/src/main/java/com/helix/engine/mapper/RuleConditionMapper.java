package com.helix.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.helix.engine.entity.RuleConditionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Rule condition table Mapper.
 */
@Mapper
public interface RuleConditionMapper extends BaseMapper<RuleConditionEntity> {

    /**
     * Batch-fetch condition nodes of the given rules, for assembling AST trees at snapshot load time.
     *
     * <p>Ordered by {@code parent_id, sort_no}, guaranteeing stable sibling order.</p>
     */
    @Select("<script>select * from t_rule_condition where deleted = 0 and rule_id in "
            + "<foreach collection='ruleIds' item='rid' open='(' separator=',' close=')'>#{rid}</foreach>"
            + " order by rule_id, parent_id, sort_no</script>")
    List<RuleConditionEntity> selectByRuleIds(@Param("ruleIds") List<Integer> ruleIds);
}
