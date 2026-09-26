package com.helix.console.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mapper
public interface SysRelationMapper {

    @Select("<script>"
            + "select ur.role_id as roleId, count(distinct ur.user_id) as cnt "
            + "from t_user_role_rel ur join t_user u on u.id = ur.user_id "
            + "where u.status = 1 and ur.status = 1 and ur.role_id in "
            + "<foreach collection='roleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "group by ur.role_id"
            + "</script>")
    List<Map<String, Object>> countUsersByRoleIdList(@Param("roleIds") List<Long> roleIds);

    default Map<Long, Integer> countUsersByRoleIds(List<Long> roleIds) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        if (roleIds == null || roleIds.isEmpty()) {
            return result;
        }
        for (Map<String, Object> row : countUsersByRoleIdList(roleIds)) {
            Object rid = row.get("roleId");
            Object cnt = row.get("cnt");
            if (rid != null && cnt != null) {
                result.put(Long.valueOf(String.valueOf(rid)), Integer.valueOf(String.valueOf(cnt)));
            }
        }
        return result;
    }

    @Delete("delete from t_user_role_rel where user_id = #{userId}")
    int deleteUserRoles(@Param("userId") Long userId);

    @Insert("insert into t_user_role_rel(user_id, role_id, organ_id, status) "
            + "values(#{userId}, #{roleId}, #{organId}, 1)")
    int insertUserRole(@Param("userId") Long userId,
                       @Param("roleId") Long roleId,
                       @Param("organId") Long organId);

    @Delete("delete from t_role_resource_rel where role_id = #{roleId}")
    int deleteRoleResources(@Param("roleId") Long roleId);

    @Insert("insert into t_role_resource_rel(role_id, resource_id) "
            + "values(#{roleId}, #{resourceId})")
    int insertRoleResource(@Param("roleId") Long roleId,
                           @Param("resourceId") Long resourceId);
}
