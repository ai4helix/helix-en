package com.helix.console.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.helix.console.system.entity.SysResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysResourceMapper extends BaseMapper<SysResource> {

    @Select("select distinct res.* from t_resource res "
            + "join t_role_resource_rel rr on rr.resource_id = res.id "
            + "join t_user_role_rel ur on ur.role_id = rr.role_id "
            + "where ur.user_id = #{userId} and ur.status = 1 and res.status = 1 "
            + "order by res.parent_id, res.id")
    List<SysResource> selectByUserId(@Param("userId") Long userId);
}
