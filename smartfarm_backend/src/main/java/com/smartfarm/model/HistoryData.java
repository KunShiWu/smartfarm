package com.smartfarm.model;

import java.util.List;
import java.util.Map;

/**
 * 历史数据（分页表格）
 */
public class HistoryData {

    /** 列元信息（与 values 的 key 对应） */
    public List<ParamItem> passageMeta;
    /** 总条数 */
    public Long total;
    public Integer page;
    public Integer perPage;
    /** 数据行：time + 各参数值 */
    public List<Row> rows;

    public static class Row {
        public String time;
        public Map<String, Double> values;

        public Row() {
        }

        public Row(String time, Map<String, Double> values) {
            this.time = time;
            this.values = values;
        }
    }
}
