package com.xmlparse.classModel;

public class ClassObject {
    private java.lang.Class ClassType;
    private Class resourceClass;

    public ClassObject(String packageStr, Class aClass) throws ClassNotFoundException {
        this.resourceClass = aClass;
        if (!aClass.isString()) {
            this.ClassType = java.lang.Class.forName(packageStr + "." + aClass.getName());
        }
    }

    public java.lang.Class getClassType() {
        return ClassType;
    }

    public Class getResourceClass() {
        return resourceClass;
    }

}
