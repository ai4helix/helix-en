package com.helix.console.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.helix.console.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT organ_id AS organId, COUNT(*) AS cnt FROM t_user " +
            "WHERE status >= 0 AND organ_id IS NOT NULL GROUP BY organ_id")
    List<Map<String, Object>> countGroupByOrgan();

    @Select("select r.role_code from t_user_role_rel ur "
            + "join t_role r on r.id = ur.role_id "
            + "where ur.user_id = #{userId} and ur.status = 1 and r.status = 1")
    List<String> selectRoleCodes(@Param("userId") Long userId);

    @Select("select r.id from t_user_role_rel ur "
            + "join t_role r on r.id = ur.role_id "
            + "where ur.user_id = #{userId} and ur.status = 1 and r.status = 1")
    List<Long> selectRoleIds(@Param("userId") Long userId);
}
