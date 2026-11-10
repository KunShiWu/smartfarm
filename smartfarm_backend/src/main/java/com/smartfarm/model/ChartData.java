package com.smartfarm.model;

import java.util.List;
import java.util.Map;

/**
 * 实时曲线数据：times 与 series 中各数组按相同顺序对齐（最新在前）
 */
public class ChartData {

    public List<String> times;
    public Map<String, List<Double>> series;

    public ChartData() {
    }

    public ChartData(List<String> times, Map<String, List<Double>> series) {
        this.times = times;
        this.series = series;
    }
}
