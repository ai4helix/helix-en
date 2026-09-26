package com.helix.engine.entity.engine.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("t_engine")
public class Engine {

	@TableId(type = IdType.AUTO)
	private Integer id;

	private String code;

	private String name;

	private String description;

	private Integer status;

	private LocalDateTime createdTime;

	private LocalDateTime updatedTime;

	private Integer createdBy;

	private Integer organId;

	@TableField(exist = false)
	private String searchString;

	@TableField(exist = false)
	private List<EngineVersion> engineVersionList;
}
