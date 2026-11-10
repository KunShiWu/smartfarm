package com.smartfarm.model;

/**
 * 农田分析结果
 */
public class FarmAnalyzeResult {

    /** 最终给农户的农事建议（markdown/纯文本） */
    public String answer;
    /** 视觉模型对截图的客观观察（未使用截图时为 null） */
    public String observation;
    /** 本次实际使用的视觉模型（未使用截图时为 null） */
    public String visionModel;
    /** 本次实际使用的推理模型 */
    public String reasonModel;
    /** 是否使用了监控截图 */
    public Boolean usedImage;
    /** 总耗时（毫秒） */
    public Long elapsedMs;
}
