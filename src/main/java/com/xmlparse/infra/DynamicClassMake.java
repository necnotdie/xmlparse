package com.xmlparse.infra;

import com.xmlparse.classModel.Class;
import com.xmlparse.classModel.ClassObject;
import com.xmlparse.classModel.Field;
import com.xmlparse.classModel.SingleClass;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import static org.objectweb.asm.Opcodes.*;

public class DynamicClassMake {
    private String path;
    private String packageStr;
    private boolean isJar;
    private List<ClassObject> classObjects;

    public DynamicClassMake(String path, String packageStr, boolean isJar) {
        this.path = path;
        this.packageStr = packageStr;
        this.isJar = isJar;
    }

    public Object ClassMake(DynamicEntryMap entryMap) throws IOException {
        Set<Map.Entry<String, List<String>>> entries = entryMap.entrySet();
        List<Class> classList = new ArrayList<Class>();
        SingleClass singleClass = null;
        SingleClass.reSet();
        for (Map.Entry<String, List<String>> entry : entries) {
            classList.add(classParse(entry.getKey(), entry.getValue()));
            singleClass = classParse(entry.getKey());
        }
        sortClassList(classList);
//        System.out.println(singleClass);
        /*for (Class aClass : classList) {
            int i = getListCount(0, aClass);
        }*/
        buildClass(singleClass);
        installClass();
        return validateClass(classList);
    }

    private Object validateClass(List<Class> classList) {
        classObjects = new ArrayList<ClassObject>();
        Object object = null;
        for (Class aClass : classList) {
            List<String> values = getClassValue(aClass);
            object = valueClass(values, aClass, object);
        }
        return object;
    }

    private Object valueClass(List<String> values, Class aClass, Object finalObject) {
        try {
            ClassObject classObject = getClassObject(aClass.getName());
            if (classObject == null) {
                classObject = new ClassObject(packageStr, aClass);
                if (classObject.getClassType() != null) {
                    classObjects.add(classObject);
                }
            }
            if (finalObject == null) {
                if (aClass.isList()) {
                    List list = new ArrayList();
                    if (aClass.isString()) {
                        for (String value : values) {
                            if(value != null) {
                                list.add(value);
                            }
                        }
                    } else {
                        for (int i = 0; i < values.size(); i++) {
                            Object object = classObject.getClassType().newInstance();
                            Field field = aClass.getFields().get(0);
                            List<String> tempValue = new ArrayList<String>();
                            tempValue.add(values.get(i));
                            java.lang.reflect.Field declareField = getObjectField(object, field.getName());
                            declareField.set(object, valueClass(tempValue, field.getRefClass(), declareField.get(object)));
                            list.add(object);
                        }
                    }
                    finalObject = list;
                } else {
                    if (aClass.isString()) {
                        finalObject = values.get(0);
                    } else {
                        Object object = classObject.getClassType().newInstance();
                        Field field = aClass.getFields().get(0);
                        java.lang.reflect.Field declareField = getObjectField(object, field.getName());
                        declareField.set(object, valueClass(values, field.getRefClass(), declareField.get(object)));
                        finalObject = object;
                    }
                }
            } else {
                if (aClass.isList()) {
                    if (finalObject instanceof List) {
                        if (!aClass.isString()) {
                            List list = (List) finalObject;
                            int size = values.size() / list.size();
                            for (int i = 0; i < list.size(); i++) {
                                List<String> tempValue = new ArrayList<String>();
                                for (int j = 0; j < size; j++) {
                                    tempValue.add(values.get(j + i * size));
                                }
                                Field field = aClass.getFields().get(0);
                                java.lang.reflect.Field declareField = getObjectField(list.get(i), field.getName());
                                Object object = valueClass(tempValue, field.getRefClass(), declareField.get(list.get(i)));
                                declareField.set(list.get(i), object);
                            }
                        }
                    }
                } else {
                    if (!aClass.isString()) {
                        Field field = aClass.getFields().get(0);
                        java.lang.reflect.Field declareField = getObjectField(finalObject, field.getName());
                        Object object = valueClass(values, field.getRefClass(), declareField.get(finalObject));
                        declareField.set(finalObject, object);
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return finalObject;
    }

    private java.lang.reflect.Field getObjectField(Object object, String name) throws NoSuchFieldException {
        java.lang.reflect.Field field = object.getClass().getDeclaredField(name);
        if (!field.isAccessible()) field.setAccessible(true);
        return field;
    }

    private ClassObject getClassObject(String name) {
        for (ClassObject classObject : classObjects) {
            if (classObject.getResourceClass().getName().equals(name)) {
                return classObject;
            }
        }
        return null;
    }

    private List<String> getClassValue(Class aClass) {
        if (aClass.isString()) {
            return aClass.getValue();
        } else {
            for (Field field : aClass.getFields()) {
                return getClassValue(field.getRefClass());
            }
        }
        return null;
    }

    private void sortClassList(List<Class> classList) {
        Class tempClass = null;
        for (int i = classList.size() - 1; i > 0; --i) {
            for (int j = 0; j < i; ++j) {
                int jcount = 0;
                int icount = 0;
                jcount = getListCount(jcount, classList.get(j + 1));
                icount = getListCount(icount, classList.get(j));
                if (jcount < icount) {
                    tempClass = classList.get(j);
                    classList.set(j, classList.get(j + 1));
                    classList.set(j + 1, tempClass);
                }
            }
        }
    }

    private int getListCount(int count, Class sourceClass) {
        for (Field field : sourceClass.getFields()) {
            if (field.getRefClass().isList()) {
                count++;
                if (!field.getRefClass().isString()) {
                    count = getListCount(count, field.getRefClass());
                }
            }
        }
        return count;
    }

    private Class classParse(String evl, List<String> value) {
        if (evl.charAt(0) == '$' && evl.charAt(1) == '{' && evl.charAt(evl.length() - 1) == '}') {
            evl = evl.substring(2, evl.length() - 1);
        }
        String[] args = evl.split(":");
        return execute(0, args, value);
    }

    private Class execute(int i, String[] args, List<String> value) {
        String arg = args[i];
        String[] cls = arg.split("\\.");
        Class baseClass;
        if (cls.length > 1) {
            baseClass = new Class(false, isList(cls[0]), isList(cls[0]) ? replaceList(cls[0]) : cls[0]);
            if (!isLast(i, args)) {
                baseClass.addField(new Field(execute(++i, args, value), cls[1]));
            } else {
                Class fieldClass = new Class(true, isList(cls[1]), isList(cls[1]) ? replaceList(cls[1]) : cls[1]);
                fieldClass.setValue(value);
                baseClass.addField(new Field(fieldClass, isList(cls[1]) ? replaceList(cls[1]) : cls[1]));
            }
        } else {
            baseClass = new Class(false, isList(cls[0]), isList(cls[0]) ? replaceList(cls[0]) : cls[0]);
            if (!isLast(i, args)) {
                Class fieldClass = execute(++i, args, value);
                baseClass.addField(new Field(fieldClass, fieldClass.getName()));
            }
        }
        return baseClass;
    }

    private SingleClass classParse(String evl) {
        boolean ignore = false;
        if (evl.charAt(0) == '$' && evl.charAt(1) == '{' && evl.charAt(evl.length() - 1) == '}') {
            evl = evl.substring(2, evl.length() - 1);
            ignore = true;
        }
        String[] args = evl.split(":");
        return execute(0, args, ignore);
    }

    private SingleClass execute(int i, String[] args, boolean ignore) {
        String arg = args[i];
        String[] cls = arg.split("\\.");
        SingleClass singleClass;
        if (cls.length > 1) {
            singleClass = SingleClass.newInstance(false, isList(cls[0]), isList(cls[0]) ? replaceList(cls[0]) : cls[0]);
            if (!isLast(i, args)) {
                singleClass.addField(new Field(execute(++i, args, ignore), cls[1], ignore));
            } else {
                SingleClass fieldClass = SingleClass.newInstance(true, isList(cls[1]), isList(cls[1]) ? replaceList(cls[1]) : cls[1]);
                singleClass.addField(new Field(fieldClass, isList(cls[1]) ? replaceList(cls[1]) : cls[1], ignore));
            }
        } else {
            singleClass = SingleClass.newInstance(false, isList(cls[0]), isList(cls[0]) ? replaceList(cls[0]) : cls[0]);
            if (!isLast(i, args)) {
                SingleClass fieldClass = execute(++i, args, ignore);
                singleClass.addField(new Field(fieldClass, fieldClass.getName(), ignore));
            }
        }
        return singleClass;
    }

    private boolean isLast(int i, String[] args) {
        if (i == args.length - 1) {
            return true;
        } else {
            return false;
        }
    }

    private boolean isList(String parame) {
        if (parame.charAt(0) == '[' && parame.charAt(parame.length() - 1) == ']') {
            return true;
        } else {
            return false;
        }
    }

    private String replaceList(String parame) {
        return parame.substring(1, parame.length() - 1);
    }

    private void installClass() {
        File file = new File(path);
        try {
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            boolean accessible = method.isAccessible(); // 获取方法的访问权限
            if (accessible == false) {
                method.setAccessible(true); // 设置方法的访问权限
            }
            // 获取系统类加载器
            URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            URL url = file.toURI().toURL();
            method.invoke(classLoader, url);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void buildClass(SingleClass singleClass) throws IOException {
        ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(V1_5, ACC_PUBLIC, packageStr.replaceAll("\\.", "/") + "/" + singleClass.getName(), null, "java/lang/Object", null);
        AnnotationVisitor annotationVisitor = classWriter.visitAnnotation("Lcom/thoughtworks/xstream/annotations/XStreamAlias;", true);
        annotationVisitor.visit("value", singleClass.getName());
        annotationVisitor.visitEnd();
        for (Field field : singleClass.getFields()) {
            if (field.getRefClass().isString()) {
                if (field.getRefClass().isList()) {
                    FieldVisitor fieldVisitor = classWriter.visitField(ACC_PRIVATE, field.getName(), "Ljava/util/List;", "Ljava/util/List<Ljava/lang/String;>;", null);
                    AnnotationVisitor fieldAnnotationVisitor = fieldVisitor.visitAnnotation("Lcom/thoughtworks/xstream/annotations/XStreamImplicit;", true);
                    fieldAnnotationVisitor.visit("itemFieldName", field.getName());
                    fieldAnnotationVisitor.visitEnd();
                    fieldVisitor.visitEnd();
                } else {
                    classWriter.visitField(ACC_PRIVATE, field.getName(), "Ljava/lang/String;", null, null).visitEnd();
                }
            } else {
                if (field.getRefClass().isList()) {
                    FieldVisitor fieldVisitor = classWriter.visitField(ACC_PRIVATE, field.getName(), "Ljava/util/List;", "Ljava/util/List<L" + packageStr.replaceAll("\\.", "/") + "/" + field.getRefClass().getName() + ";>;", null);
                    AnnotationVisitor fieldAnnotationVisitor = fieldVisitor.visitAnnotation("Lcom/thoughtworks/xstream/annotations/XStreamAlias;", true);
                    fieldAnnotationVisitor.visit("value", field.getName());
                    fieldAnnotationVisitor.visitEnd();
                    fieldVisitor.visitEnd();
                } else {
                    classWriter.visitField(ACC_PRIVATE, field.getName(), "L" + packageStr.replaceAll("\\.", "/") + "/" + field.getName() + ";", null, null).visitEnd();
                }
                SingleClass fieldRefClass = (SingleClass) field.getRefClass();
                buildClass(fieldRefClass);
            }
        }
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
        classWriter.visitEnd();
//        System.out.println((path.charAt(path.length() - 1) == '\\' ? path : path + "\\") + packageStr.replaceAll("\\.", "\\\\") + "\\" + singleClass.getName());
        File file = new File((path.charAt(path.length() - 1) == '\\' ? path : path + "\\") + packageStr.replaceAll("\\.", "\\\\") + "\\" + singleClass.getName() + ".class");
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        fileOutputStream.write(classWriter.toByteArray());
        fileOutputStream.flush();
        fileOutputStream.close();
    }

    public java.lang.Class[] getMakeClass() {
        java.lang.Class[] classes = new java.lang.Class[classObjects.size()];
        for (int i = 0; i < classObjects.size(); i++) {
            classes[i] = classObjects.get(i).getClassType();
        }
        return classes;
    }
}
