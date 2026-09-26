package com.helix.console.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.helix.console.system.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("select resource_id from t_role_resource_rel where role_id = #{roleId}")
    List<Long> selectResourceIds(@Param("roleId") Long roleId);

    @Select("SELECT role_id AS roleId, COUNT(*) AS cnt FROM t_user_role_rel rel " +
            "JOIN t_user u ON u.id = rel.user_id AND u.status >= 0 " +
            "GROUP BY role_id")
    List<Map<String, Object>> countUsersByRoleIds();

    @Select("SELECT organ_id AS organId, COUNT(*) AS cnt FROM t_role " +
            "WHERE status >= 0 AND organ_id IS NOT NULL GROUP BY organ_id")
    List<Map<String, Object>> countGroupByOrgan();
}
