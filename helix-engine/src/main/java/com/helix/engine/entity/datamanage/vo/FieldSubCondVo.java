
package com.helix.engine.entity.datamanage.vo;

import java.io.Serializable;

public class FieldSubCondVo implements Serializable{

	private static final long serialVersionUID = 1L;

	//[{"fieldId":"43","operator":"in","fieldValue":"b","logical":"and"}]
	
	private Integer fieldId;
	
	private String operator;
	
	private String fieldValue;
	
	private String logical;
	
	private Integer valueType;
	
	private String valueScope;
	
    private String[] values;
    
	private String fieldCn;
	
	
	public Integer getFieldId() {
		return fieldId;
	}
	public void setFieldId(Integer fieldId) {
		this.fieldId = fieldId;
	}
	public String getOperator() {
		return operator;
	}
	public void setOperator(String operator) {
		this.operator = operator;
	}
	public String getFieldValue() {
		return fieldValue;
	}
	public void setFieldValue(String fieldValue) {
		this.fieldValue = fieldValue;
	}
	public String getLogical() {
		return logical;
	}
	public void setLogical(String logical) {
		this.logical = logical;
	}
	public Integer getValueType() {
		return valueType;
	}
	public void setValueType(Integer valueType) {
		this.valueType = valueType;
	}
	public String getValueScope() {
		return valueScope;
	}
	public void setValueScope(String valueScope) {
		this.valueScope = valueScope;
	}
	public String[] getValues() {
		if(valueType == 3){
			values = valueScope.split(",");
		}else{
			values = new String[]{valueScope}; 
		}
		return values;
	}
	public void setValues(String[] values) {
		this.values = values;
	}
	public String getFieldCn() {
		return fieldCn;
	}
	public void setFieldCn(String fieldCn) {
		this.fieldCn = fieldCn;
	}
	
	
	
}
