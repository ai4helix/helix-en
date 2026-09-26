package com.helix.engine.entity.engine.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Result {
	private String resultType;
	private Integer id;
	private String code;
	private String name;
	private String value;
	private Map<String, Object> map;
	private List<Rule> list;
	private String expression;
}
