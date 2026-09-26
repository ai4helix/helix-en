package com.helix.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.helix.engine.entity.engine.model.Engine;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EngineMapper extends BaseMapper<Engine> {
}
