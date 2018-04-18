package com.xmlparse.core;

import com.XmlScript.ruleutil.RulesLoad;
import com.sun.istack.internal.Nullable;
import com.thoughtworks.xstream.XStream;
import com.xmlparse.infra.DynamicClassMake;
import com.xmlparse.infra.DynamicEntryMap;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class XmlToBean {
    private Properties properties = new Properties();
    private Class[] makeClass;
    private boolean enableEvl = false;
    private String rulePath;

    public Object getBean(String xml) throws DocumentException, IOException {
        DynamicClassMake dynamicClassMake = new DynamicClassMake(System.getProperty("user.dir") + "\\model", "com.ZDB", false);
        FileInputStream fileInputStream = new FileInputStream(new File(System.getProperty("user.dir") + "\\target\\classes\\entry.properties"));
        properties.load(fileInputStream);
        Document document = DocumentHelper.parseText(xml);
        DynamicEntryMap dynamicEntry = getDynamicEntryMap(document.getRootElement());
        loadHead(dynamicEntry);
        Object object = dynamicClassMake.ClassMake(dynamicEntry);
        this.makeClass = dynamicClassMake.getMakeClass();
        return object;
    }

    public Object getBean(String xml, boolean enableEvl, @Nullable String rulePath) throws IOException, DocumentException {
        this.enableEvl = enableEvl;
        this.rulePath = rulePath;
        return getBean(xml);
    }

    private void loadHead(DynamicEntryMap dynamicEntry) {
        Set<Object> keySet = properties.keySet();
        for (Object o : keySet) {
            if (o.toString().charAt(0) == '$' && o.toString().charAt(1) == '{' && o.toString().charAt(o.toString().length() - 1) == '}') {
                String value = properties.getProperty(o.toString());
                dynamicEntry.add(o.toString(), enableEvl ? loadEvl(value) : value);
            }
        }
    }

    private String loadEvl(String evl) {
        try {
            RulesLoad.Load(rulePath, "");
            return RulesLoad.execute(evl).toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return evl;
    }

    public Class[] getMakeClass() {
        return makeClass;
    }

    public DynamicEntryMap getDynamicEntryMap(Element element) {
        synchronizElement(element);
//        System.out.println(element.getDocument().asXML());
        return getElementName(element, null);
    }

    private void synchronizElement(Element element) {
        Set<String> nameSet = new HashSet<String>();
        for (Object item : element.elements()) {
            Element itemElement = (Element) item;
            nameSet.add(itemElement.getName());
        }
        List<List<Element>> elementList = new ArrayList<List<Element>>();
        for (String name : nameSet) {
            List<Element> elements = element.elements(name);
            synchronizElements(elements);
        }
    }

    private void synchronizElements(List<Element> elements) {
        if (!isEnd(elements)) {
            for (int i = 1; i < elements.size(); i++) {
                for (int j = 0; j < i; j++) {
                    synchronizElements(elements.get(j), elements.get(i));
                }
            }
            Set<String> nameSet = new HashSet<String>();
            for (Element element : elements) {
                for (Object object : element.elements()) {
                    Element itemElement = (Element) object;
                    nameSet.add(itemElement.getName());
                }
            }
            for (String name : nameSet) {
                List<Element> elementList = new ArrayList<Element>();
                for (Element element : elements) {
                    elementList.addAll(element.elements(name));
                }
                synchronizElements(elementList);
            }

        }
    }

    private void synchronizElements(Element element1, Element element2) {
        Set<String> nameSet1 = new HashSet<String>();
        for (Object object : element1.elements()) {
            Element element = (Element) object;
            nameSet1.add(element.getName());
        }
        for (String name : nameSet1) {
            List<Element> elements = element2.elements(name);
            for (int i = 0; i < element1.elements(name).size() - (elements == null ? 0 : elements.size()); i++) {
                element2.add(DocumentHelper.createElement(name));
            }
        }
        Set<String> nameSet2 = new HashSet<String>();
        for (Object object : element2.elements()) {
            Element element = (Element) object;
            nameSet2.add(element.getName());
        }
        for (String name : nameSet2) {
            List<Element> elements = element1.elements(name);
            for (int i = 0; i < element2.elements(name).size() - (elements == null ? 0 : elements.size()); i++) {
                element1.add(DocumentHelper.createElement(name));
            }
        }
    }

    public boolean isEnd(List<Element> elements) {
        for (Element element : elements) {
            if (element.elements() != null && element.elements().size() > 0) {
                return false;
            }
        }
        return true;
    }

    private DynamicEntryMap getElementName(Element element, DynamicEntryMap dynamicEntry) {
        if (dynamicEntry == null) dynamicEntry = new DynamicEntryMap();
        List<Element> elements = element.elements();
        if (elements != null) {
            if (elements.size() > 0) {
                for (Element item : elements)
                    getElementName(item, dynamicEntry);
            } else {
//                System.out.println(element.getPath());
                String key = element.getPath().replaceFirst("/", "").replaceAll("/", ".");
                String value = properties.getProperty(key);
                if (value != null) {
                    dynamicEntry.add(value, "".equals(element.getText())?null:element.getText());
                }
            }
        }
        return dynamicEntry;
    }

    public static void main(String[] args) throws IOException, DocumentException {
        FileInputStream fileInputStream = new FileInputStream(new File(System.getProperty("user.dir") + "\\target\\classes\\ZDBpolicy_copy.xml"));
        byte[] bytes = new byte[1024];
        int length;
        StringBuffer stringBuffer = new StringBuffer();
        while ((length = fileInputStream.read(bytes)) != -1) {
            stringBuffer.append(new String(bytes, 0, length));
        }
        fileInputStream.close();
        XmlToBean xmlToBean = new XmlToBean();
        Object bean = xmlToBean.getBean(stringBuffer.toString(), true, "/rule.xml");
        fileInputStream.close();
        XStream xStream = new XStream();
        xStream.processAnnotations(xmlToBean.getMakeClass());
        System.out.println("转化后：" + xStream.toXML(bean));
    }
}
