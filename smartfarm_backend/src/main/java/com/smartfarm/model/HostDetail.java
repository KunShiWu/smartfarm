package com.smartfarm.model;

import java.util.List;

/**
 * 主机实时详情（实时参数页）
 */
public class HostDetail {

    public String id;
    public String name;
    public String group;
    public Boolean online;
    public Double battery;
    public Integer interval;
    public String collectTime;
    public List<ParamItem> paramMeta;
    public List<ParamItem> params;
}
