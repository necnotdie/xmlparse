package com.xmlparse.classModel;

import java.util.ArrayList;
import java.util.List;

public class Class {
    private boolean isString;
    private String name;
    private List<Field> fields;
    private boolean isTable;
    private boolean isList;
    private List<String> value;
    private Field resourceField;

    public Class(boolean isString, boolean isList, String name) {
        this.isString = isString;
        this.isList = isList;
        this.name = name;
        fields = new ArrayList<Field>();
        isTable = false;
    }

    public boolean isTable() {
        return this.isTable;
    }

    public void addField(Field field) {
        boolean isExists = false;
        for (Field itemField : this.fields) {
            if (itemField.getName().equals(field.getName())) {
                isExists = true;
                return;
            }
        }
        if (!isExists) {
            this.fields.add(field);
            field.setResourceClass(this);
        }
        if (field.getRefClass().isString) {
            this.isTable = true;
        }
    }

    public String getName() {
        return this.name;
    }

    public Field getField(String fieldName) {
        for (Field field : fields) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    public List<String> getValue() {
        return value;
    }

    public void setValue(List<String> value) {
        this.value = value;
    }

    public List<Field> getFields() {
        return this.fields;
    }

    public boolean isList() {
        return this.isList;
    }

    public boolean isString() {
        return this.isString;
    }

    public Field getResourceField() {
        return resourceField;
    }

    public void setResourceField(Field resourceField) {
        this.resourceField = resourceField;
    }
}
