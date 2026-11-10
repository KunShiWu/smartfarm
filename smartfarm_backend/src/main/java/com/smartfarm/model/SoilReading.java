package com.smartfarm.model;

/**
 * 土壤检测读数（自动喷灌判断依据）
 */
public class SoilReading {

    public String hostId;
    public String hostName;
    /** 土壤湿度（%） */
    public Double moisture;
    public String collectTime;
}
