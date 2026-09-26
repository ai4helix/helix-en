package com.helix.console.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.helix.console.engine.entity.DtRowEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DtRowMapper extends BaseMapper<DtRowEntity> {
}
