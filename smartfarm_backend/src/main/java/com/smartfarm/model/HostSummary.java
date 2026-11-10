package com.smartfarm.model;

/**
 * 主机概要（主机列表）
 */
public class HostSummary {

    public String id;
    public String name;
    public String group;
    public Boolean online;
    public Double battery;
    public Integer interval;
    public String collectTime;
    /** 设备类型：host=传感器主机，camera=摄像头 */
    public String type = "host";

    public HostSummary() {
    }

    public HostSummary(String id, String name, String group, Boolean online, Integer interval, String collectTime) {
        this.id = id;
        this.name = name;
        this.group = group;
        this.online = online;
        this.interval = interval;
        this.collectTime = collectTime;
    }
}
