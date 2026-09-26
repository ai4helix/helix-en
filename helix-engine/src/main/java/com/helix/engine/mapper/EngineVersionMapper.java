package com.helix.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.helix.engine.entity.engine.model.EngineVersion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EngineVersionMapper extends BaseMapper<EngineVersion> {
}
