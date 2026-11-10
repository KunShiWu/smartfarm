package com.smartfarm.model;

/**
 * 修改记录
 */
public class ModifyRecord {

    public String hostId;
    public String time;
    public String field;
    public String before;
    public String after;
    public String operator;

    public ModifyRecord() {
    }

    public ModifyRecord(String hostId, String time, String field, String before, String after, String operator) {
        this.hostId = hostId;
        this.time = time;
        this.field = field;
        this.before = before;
        this.after = after;
        this.operator = operator;
    }
}
