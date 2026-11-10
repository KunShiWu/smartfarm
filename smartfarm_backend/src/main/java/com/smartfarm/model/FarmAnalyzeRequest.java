package com.smartfarm.model;

import java.util.List;

/**
 * 农田分析请求：来自前端
 */
public class FarmAnalyzeRequest {

    /** 摄像头截图，data URL 或纯 base64（可空，缺失时仅用环境数据做文本分析） */
    public String imageBase64;
    /** 农户的提问（可空，缺省用内置默认问题） */
    public String question;
    /** 监控点位/摄像头名称（可空） */
    public String deviceName;
    /** 截图时间 yyyy-MM-dd HH:mm:ss（可空） */
    public String captureTime;
    /** 当前环境/土壤参数（可空） */
    public List<ParamItem> params;

}
