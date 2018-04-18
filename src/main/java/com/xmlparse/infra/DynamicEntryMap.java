package com.xmlparse.infra;

import java.util.*;

public class DynamicEntryMap extends HashMap<String, List<String>> {

    public void add(String key, String value) {
        if (super.containsKey(key)) {
            List<String> valueList = super.get(key) == null ? new ArrayList<String>() : super.get(key);
            valueList.add(value);
            super.put(key, valueList);
        } else {
            List<String> valueList = new ArrayList<String>();
            valueList.add(value);
            super.put(key, valueList);
        }
    }

    @Override
    public Set<Map.Entry<String, List<String>>> entrySet() {
        return super.entrySet();
    }
}
