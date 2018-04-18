package com.XmlScript.rulebean;

public class RuleBean {
	private String beanName;
	private String beanType;
	private String beanValue;
	private Object value;
	public String getBeanName() {
		return beanName;
	}
	public void setBeanName(String beanName) {
		this.beanName = beanName;
	}
	public String getBeanType() {
		return beanType;
	}
	public void setBeanType(String beanType) {
		this.beanType = beanType;
	}
	public String getBeanValue() {
		return beanValue;
	}
	public void setBeanValue(String beanValue) {
		this.beanValue = beanValue;
	}
	public Object getValue() {
		return value;
	}
	public void setValue(Object value) {
		this.value = value;
	}
}
