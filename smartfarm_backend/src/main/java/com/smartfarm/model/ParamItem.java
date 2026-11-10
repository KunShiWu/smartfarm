package com.smartfarm.model;

/**
 * 参数项：paramMeta 时 value 为空；实时参数时带 value
 */
public class ParamItem {

    public String key;
    public String name;
    public String unit;
    public Double value;

    public ParamItem() {
    }

    public ParamItem(String key, String name, String unit) {
        this.key = key;
        this.name = name;
        this.unit = unit;
    }

    public ParamItem(String key, String name, String unit, Double value) {
        this.key = key;
        this.name = name;
        this.unit = unit;
        this.value = value;
    }
}
