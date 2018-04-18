package com.xmlparse.classModel;

import java.util.ArrayList;
import java.util.List;

public class SingleClass extends Class {
    private static List<SingleClass> classSet = new ArrayList<SingleClass>();

    private SingleClass(boolean isString, boolean isList, String name) {
        super(isString, isList, name);
    }

    public static SingleClass newInstance(boolean isString, boolean isList, String name) {
        SingleClass singleClass = getClass(name);
        if (singleClass == null) {
            singleClass = new SingleClass(isString, isList, name);
            classSet.add(singleClass);
        }
        return singleClass;
    }

    private static SingleClass getClass(String name) {
        for (SingleClass aClass : classSet) {
            if (aClass.getName().equals(name)) {
                return aClass;
            }
        }
        return null;
    }

    public static void reSet() {
        classSet.clear();
    }
}
