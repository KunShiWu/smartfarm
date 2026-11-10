package com.smartfarm.model;

/**
 * 喷灌设备状态（前端 src/api/index.js 约定结构一一对应）
 */
public class IrrigationState {

    /** 设备 id（设备列表中的虚拟设备） */
    public String id = "irrigation-pump";
    public String name = "喷灌设备";
    public String group = "智能灌溉";
    /** 桥接服务是否可达 */
    public Boolean online;
    /** 水泵开关状态 */
    public Boolean on;
    /** 控制模式：auto=按土壤湿度自动控制，manual=手动控制 */
    public String mode = "auto";
    /** 土壤湿度阈值（%） */
    public Double threshold;
    /** 单次喷灌时长（分钟） */
    public Integer durationMinutes;
    /** 最近一次读取到的土壤湿度（%） */
    public Double soilMoisture;
    /** 土壤数据来源主机 */
    public String soilHostName;
    /** 土壤数据采集时间 */
    public String soilCollectTime;
    /** 本次喷灌剩余秒数（未喷灌时为 null） */
    public Long remainingSeconds;
    /** 最近一次动作说明 */
    public String lastAction;
    public String lastActionAt;
    /** 最近一次动作来源：auto/manual/timeout/device */
    public String lastActionSource;
    /** 是否已配置桥接 Token */
    public Boolean bridgeConfigured;
    public String updatedAt;
}
