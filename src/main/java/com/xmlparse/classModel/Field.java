package com.xmlparse.classModel;

public class Field {
    private Class refClass;
    private String name;
    private Class resourceClass;
    private boolean ignore = false;

    public Field(Class refClass, String name) {
        this.refClass = refClass;
        this.name = name;
        refClass.setResourceField(this);
    }

    public Field(Class refClass, String name, boolean ignore) {
        this(refClass, name);
        this.ignore = ignore;
    }

    public Class getRefClass() {
        return this.refClass;
    }

    public String getName() {
        return this.name;
    }

    public void setResourceClass(Class resourceClass) {
        this.resourceClass = resourceClass;
    }

    public Class getResourceClass() {
        return resourceClass;
    }

    public boolean isIgnore() {
        return ignore;
    }
}
