package com.smartfarm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartfarm.client.MijiaBridgeClient;
import com.smartfarm.client.MordenIotClient;
import com.smartfarm.common.BizException;
import com.smartfarm.config.AppProperties;
import com.smartfarm.model.ChartData;
import com.smartfarm.model.HistoryData;
import com.smartfarm.model.HostDetail;
import com.smartfarm.model.HostSummary;
import com.smartfarm.model.ParamItem;
import com.smartfarm.model.SoilReading;
import com.smartfarm.model.VideoLive;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主机业务服务：对接上游开放平台，做字段映射与短 TTL 缓存（规避上游限流）
 */
@Service
public class HostService {

    /** 参数中文名 → 前端 key 映射（保持与前端约定的 8 参数 key） */
    private static final Map<String, String> PARAM_KEY_MAP = new LinkedHashMap<>();

    static {
        PARAM_KEY_MAP.put("土壤温度", "temp");
        PARAM_KEY_MAP.put("土壤湿度", "humidity");
        PARAM_KEY_MAP.put("土壤电导率", "ec");
        PARAM_KEY_MAP.put("土壤盐分", "salt");
        PARAM_KEY_MAP.put("土壤氮含量", "nitrogen");
        PARAM_KEY_MAP.put("土壤磷含量", "phosphorus");
        PARAM_KEY_MAP.put("土壤钾含量", "potassium");
        PARAM_KEY_MAP.put("土壤pH值", "ph");
    }

    /** 历史数据中不纳入曲线的通道 */
    private static final String BATTERY_PARAM_NAME = "电池电压";

    /** 喷灌设备（虚拟设备）在设备列表中的 id 与分组 */
    public static final String IRRIGATION_DEVICE_ID = "irrigation-pump";
    private static final String IRRIGATION_GROUP = "智能灌溉";
    /** 土壤湿度参数 key */
    private static final String SOIL_MOISTURE_KEY = "humidity";

    private static final long HOST_LIST_TTL = 30_000;
    private static final long REALTIME_TTL = 10_000;
    private static final long CHART_TTL = 60_000;

    /** 历史数据时间格式 */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 曲线接口单次最大抽样点（上游每页 100 条，最多抽 6 页，规避 30 次/分钟限流） */
    private static final int HISTORY_CHART_MAX_SAMPLE = 600;
    private static final int HISTORY_PAGE_SIZE = 100;

    private final MordenIotClient client;
    private final AppProperties props;
    private final ModifyRecordService recordService;
    private final EzvizService ezvizService;
    private final MijiaBridgeClient mijiaBridgeClient;

    private volatile CacheEntry<JsonNode> hostListCache;
    private final Map<Long, CacheEntry<HostDetail>> realtimeCache = new ConcurrentHashMap<>();
    private final Map<Long, CacheEntry<ChartData>> chartCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Object>> historyCache = new ConcurrentHashMap<>();

    public HostService(MordenIotClient client, AppProperties props, ModifyRecordService recordService,
                       EzvizService ezvizService, MijiaBridgeClient mijiaBridgeClient) {
        this.client = client;
        this.props = props;
        this.recordService = recordService;
        this.ezvizService = ezvizService;
        this.mijiaBridgeClient = mijiaBridgeClient;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 主机列表 */
    public List<HostSummary> listHosts() {
        JsonNode list = rawDeviceList();
        List<HostSummary> hosts = new ArrayList<>();
        for (JsonNode d : list) {
            hosts.add(new HostSummary(
                    d.path("id").asText(),
                    d.path("name").asText(),
                    props.getHostGroup(),
                    "ONLINE".equals(d.path("status").asText()),
                    secondsToMinutes(d.path("collection_interval_time").asLong()),
                    d.path("ts_time").asText(null)));
        }
        appendCamera(hosts);
        appendIrrigation(hosts);
        return hosts;
    }

    /**
     * 把萤石摄像头作为一条虚拟设备追加到设备列表。
     *
     * <p>只取设备状态（名称/在线），不请求直播地址：设备离线时直播地址接口会报
     * 「设备不在线」，但设备条目仍应展示为离线而非直接消失。萤石接口不可用时
     * 也保留该条目（兜底为离线），不影响主机列表返回。
     */
    private void appendCamera(List<HostSummary> hosts) {
        HostSummary camera = new HostSummary();
        camera.id = "camera-" + props.getEzviz().getDeviceSerial();
        camera.name = "摄像头";
        camera.group = "视频监控";
        camera.online = Boolean.FALSE;
        camera.type = "camera";
        try {
            VideoLive status = ezvizService.getDeviceStatus();
            if (status.deviceName != null && !status.deviceName.isEmpty()) {
                camera.name = status.deviceName;
            }
            if (status.online != null) {
                camera.online = status.online;
            }
        } catch (Exception ignored) {
            // 萤石接口不可用时仍保留摄像头条目(离线)
        }
        hosts.add(camera);
    }

    /**
     * 把喷灌设备（米家水泵开关）作为一条虚拟设备追加到设备列表。
     * 在线状态取桥接服务 /health 的探测结果。
     */
    private void appendIrrigation(List<HostSummary> hosts) {
        HostSummary pump = new HostSummary();
        pump.id = IRRIGATION_DEVICE_ID;
        pump.name = "喷灌设备";
        pump.group = IRRIGATION_GROUP;
        pump.type = "irrigation";
        pump.online = mijiaBridgeClient.isReachable();
        hosts.add(pump);
    }

    /**
     * 读取土壤检测主机的土壤湿度（自动喷灌判断依据）。
     * 无设备、无湿度通道或上游异常时返回 null，由调用方决定是否跳过判断。
     */
    public SoilReading readSoilMoisture() {
        JsonNode list = rawDeviceList();
        if (!list.isArray() || list.size() == 0) {
            return null;
        }
        long hostId = list.get(0).path("id").asLong();
        if (hostId <= 0) {
            return null;
        }
        HostDetail detail = getRealtime(hostId);

        SoilReading reading = new SoilReading();
        reading.hostId = String.valueOf(hostId);
        reading.hostName = detail.name;
        reading.collectTime = detail.collectTime;
        for (ParamItem p : detail.params) {
            if (SOIL_MOISTURE_KEY.equals(p.key)) {
                reading.moisture = p.value;
                break;
            }
        }
        return reading;
    }

    /** 第一台设备的原始数据（供天气定位使用），无设备时返回 null */
    public JsonNode firstDevice() {
        JsonNode list = rawDeviceList();
        return list.isArray() && list.size() > 0 ? list.get(0) : null;
    }

    /** 主机实时参数 */
    public HostDetail getRealtime(long hostId) {
        CacheEntry<HostDetail> cached = realtimeCache.get(hostId);
        if (cached != null && !cached.expired()) {
            return cached.value;
        }
        JsonNode data = client.realtimeInfo(hostId);
        HostDetail detail = mapRealtime(data);
        realtimeCache.put(hostId, new CacheEntry<>(detail, REALTIME_TTL));
        return detail;
    }

    /** 实时曲线（最近 24 小时内的最新 N 个点，最新在前） */
    public ChartData getChart(long hostId) {
        CacheEntry<ChartData> cached = chartCache.get(hostId);
        if (cached != null && !cached.expired()) {
            return cached.value;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String end = LocalDateTime.now().format(fmt);
        String start = LocalDateTime.now().minusHours(24).format(fmt);
        JsonNode data = client.historyList(hostId, 1, props.getChartPoints(), start, end);

        // passage 数组顺序与 sheet_list.ling_data 数值顺序严格对应
        List<String> keys = new ArrayList<>();
        for (JsonNode p : data.path("passage")) {
            String name = p.path("nickname").asText("");
            if (BATTERY_PARAM_NAME.equals(name)) {
                keys.add(null);
                continue;
            }
            keys.add(keyOf(name, p.path("passage_id").asText()));
        }

        Map<String, List<Double>> series = new LinkedHashMap<>();
        List<String> times = new ArrayList<>();
        for (JsonNode sheet : data.path("sheet_list")) {
            times.add(sheet.path("ts").asText());
            JsonNode values = sheet.path("ling_data");
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                if (key == null) {
                    continue;
                }
                series.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(parseDouble(values.path(i).asText(null)));
            }
        }

        ChartData chart = new ChartData(times, series);
        chartCache.put(hostId, new CacheEntry<>(chart, CHART_TTL));
        return chart;
    }

    // ------------------------------------------------------------------
    // 历史数据
    // ------------------------------------------------------------------

    /** 历史数据（分页表格，最新在前） */
    @SuppressWarnings("unchecked")
    public HistoryData getHistory(long hostId, String startTime, String endTime, int page, int perPage) {
        validateTime(startTime, "startTime");
        validateTime(endTime, "endTime");
        page = Math.max(1, page);
        perPage = Math.min(Math.max(1, perPage), HISTORY_PAGE_SIZE);

        String cacheKey = "table:" + hostId + ":" + startTime + ":" + endTime + ":" + page + ":" + perPage;
        CacheEntry<Object> cached = historyCache.get(cacheKey);
        if (cached != null && !cached.expired()) {
            return (HistoryData) cached.value;
        }

        JsonNode data = client.historyList(hostId, page, perPage, startTime, endTime);

        HistoryData result = new HistoryData();
        List<String> keys = new ArrayList<>();
        result.passageMeta = buildPassageMeta(data.path("passage"), keys);
        result.total = data.path("sheet_total").asLong();
        result.page = page;
        result.perPage = perPage;
        result.rows = new ArrayList<>();
        for (JsonNode sheet : data.path("sheet_list")) {
            result.rows.add(new HistoryData.Row(sheet.path("ts").asText(), extractValues(sheet, keys)));
        }

        historyCache.put(cacheKey, new CacheEntry<>(result, CHART_TTL));
        return result;
    }

    /**
     * 历史曲线（时间升序）。
     *
     * <p>范围内数据量较大时，先取第 1 页拿到总数，再按均匀步长跨页抽样（最多 6 页 / 600 点），
     * 最后按目标点数等距降采样，保证整段时间范围都被覆盖。
     */
    @SuppressWarnings("unchecked")
    public ChartData getHistoryChart(long hostId, String startTime, String endTime, int points) {
        validateTime(startTime, "startTime");
        validateTime(endTime, "endTime");
        points = Math.min(Math.max(10, points), 500);

        String cacheKey = "chart:" + hostId + ":" + startTime + ":" + endTime + ":" + points;
        CacheEntry<Object> cached = historyCache.get(cacheKey);
        if (cached != null && !cached.expired()) {
            return (ChartData) cached.value;
        }

        JsonNode firstPage = client.historyList(hostId, 1, HISTORY_PAGE_SIZE, startTime, endTime);
        long total = firstPage.path("sheet_total").asLong();

        // 合并去重（key=数据id），跨页均匀抽样
        Map<Long, JsonNode> sheetMap = new LinkedHashMap<>();
        firstPage.path("sheet_list").forEach(s -> sheetMap.put(s.path("id").asLong(), s));

        long totalPages = (total + HISTORY_PAGE_SIZE - 1) / HISTORY_PAGE_SIZE;
        int pagesToFetch = (int) Math.min(totalPages, HISTORY_CHART_MAX_SAMPLE / HISTORY_PAGE_SIZE);
        if (totalPages > 1 && pagesToFetch > 1) {
            for (int i = 1; i < pagesToFetch; i++) {
                // 在 [1, totalPages] 上均匀取页码（第 1 页已取）
                long pageNo = Math.round(i * (totalPages - 1) * 1.0 / (pagesToFetch - 1)) + 1;
                JsonNode paged = client.historyList(hostId, (int) pageNo, HISTORY_PAGE_SIZE, startTime, endTime);
                paged.path("sheet_list").forEach(s -> sheetMap.putIfAbsent(s.path("id").asLong(), s));
            }
        }

        // 时间升序排列
        List<JsonNode> sheets = new ArrayList<>(sheetMap.values());
        sheets.sort(Comparator.comparing(s -> s.path("ts").asText()));

        // 等距降采样到目标点数
        int step = Math.max(1, sheets.size() / points);
        List<JsonNode> sampled = new ArrayList<>();
        for (int i = 0; i < sheets.size(); i += step) {
            sampled.add(sheets.get(i));
        }

        List<String> keys = new ArrayList<>();
        buildPassageMeta(firstPage.path("passage"), keys);

        List<String> times = new ArrayList<>();
        Map<String, List<Double>> series = new LinkedHashMap<>();
        for (JsonNode sheet : sampled) {
            times.add(sheet.path("ts").asText());
            extractValues(sheet, keys)
                    .forEach((k, v) -> series.computeIfAbsent(k, x -> new ArrayList<>()).add(v));
        }

        ChartData chart = new ChartData(times, series);
        historyCache.put(cacheKey, new CacheEntry<>(chart, CHART_TTL));
        return chart;
    }

    /** 解析 passage 数组：返回列元信息，并把对应 key（含电池位 null 占位）写入 keys */
    private List<ParamItem> buildPassageMeta(JsonNode passage, List<String> keys) {
        List<ParamItem> meta = new ArrayList<>();
        for (JsonNode p : passage) {
            String name = p.path("nickname").asText("");
            if (BATTERY_PARAM_NAME.equals(name)) {
                keys.add(null);
                continue;
            }
            String key = keyOf(name, p.path("passage_id").asText());
            keys.add(key);
            meta.add(new ParamItem(key, name, p.path("en_unit").asText("")));
        }
        return meta;
    }

    /** 按 keys（与 ling_data 下标对齐，null 跳过）提取一行数值 */
    private Map<String, Double> extractValues(JsonNode sheet, List<String> keys) {
        Map<String, Double> values = new LinkedHashMap<>();
        JsonNode lingData = sheet.path("ling_data");
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            if (key != null) {
                values.put(key, parseDouble(lingData.path(i).asText(null)));
            }
        }
        return values;
    }

    private static void validateTime(String time, String field) {
        if (time == null || time.trim().isEmpty()) {
            throw new BizException("缺少参数: " + field + "（格式 yyyy-MM-dd HH:mm:ss）");
        }
        try {
            LocalDateTime.parse(time.trim(), TIME_FMT);
        } catch (DateTimeParseException e) {
            throw new BizException(field + " 格式错误，应为 yyyy-MM-dd HH:mm:ss: " + time);
        }
    }

    // ------------------------------------------------------------------
    // 修改操作
    // ------------------------------------------------------------------

    /** 修改采集间隔（分钟） */
    public void updateInterval(long hostId, int minutes) {
        if (minutes < 1) {
            throw new BizException("采集间隔不能小于 1 分钟");
        }
        HostDetail current = getRealtime(hostId);
        int old = current.interval == null ? 0 : current.interval;

        client.setUpdateSecond(hostId, minutes * 60L);

        recordService.add(hostId, "采集间隔", old + "分钟", minutes + "分钟");
        evict(hostId);
    }

    /** 修改主机设置（目前支持主机名称） */
    public void updateSettings(long hostId, String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BizException("主机名称不能为空");
        }
        JsonNode info = client.deviceInfo(hostId);
        String oldName = info.path("name").asText();
        long timeZoneId = info.path("time_zone_id").asLong(17);

        client.editDevice(hostId, name.trim(), timeZoneId);

        if (!name.trim().equals(oldName)) {
            recordService.add(hostId, "主机名称", oldName, name.trim());
        }
        evict(hostId);
    }

    /** 修改记录 */
    public List<com.smartfarm.model.ModifyRecord> getModifyRecords(long hostId) {
        return recordService.list(hostId);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private JsonNode rawDeviceList() {
        CacheEntry<JsonNode> cached = hostListCache;
        if (cached != null && !cached.expired()) {
            return cached.value;
        }
        JsonNode data = client.listMyDevices(1, 100);
        JsonNode list = data.path("list");
        hostListCache = new CacheEntry<>(list, HOST_LIST_TTL);
        return list;
    }

    private HostDetail mapRealtime(JsonNode data) {
        JsonNode info = data.path("device_info");

        HostDetail detail = new HostDetail();
        detail.id = info.path("id").asText();
        detail.name = info.path("name").asText();
        detail.group = props.getHostGroup();
        detail.online = "ONLINE".equals(info.path("status").asText());
        detail.interval = secondsToMinutes(info.path("collection_interval_time").asLong());
        detail.collectTime = info.path("ts_time").asText(null);
        double voltage = info.path("voltage").asDouble(Double.NaN);
        if (Double.isNaN(voltage)) {
            voltage = info.path("bv").asDouble(Double.NaN);
        }
        detail.battery = Double.isNaN(voltage) ? null : voltage;

        List<ParamItem> meta = new ArrayList<>();
        List<ParamItem> values = new ArrayList<>();
        for (JsonNode p : data.path("passage")) {
            String paramName = p.path("parameter_name").asText("");
            String nickname = p.path("nickname").asText(null);
            String displayName = (nickname != null && !nickname.isEmpty() && !"null".equals(nickname))
                    ? nickname : paramName;
            String key = keyOf(paramName, p.path("id").asText());
            String unit = p.path("en_unit").asText("");
            meta.add(new ParamItem(key, displayName, unit));
            values.add(new ParamItem(key, displayName, unit, parseDouble(p.path("param_value").asText(null))));
        }
        detail.paramMeta = meta;
        detail.params = values;
        return detail;
    }

    /** 参数名 → 前端约定 key；未知名称回退为 param_{通道id} */
    private static String keyOf(String name, String passageId) {
        String key = PARAM_KEY_MAP.get(name);
        return key != null ? key : "param_" + passageId;
    }

    private static Integer secondsToMinutes(long seconds) {
        if (seconds <= 0) {
            return null;
        }
        return Math.max(1, Math.round(seconds / 60.0f));
    }

    private static Double parseDouble(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 使指定主机的全部缓存失效（修改配置后调用） */
    private void evict(long hostId) {
        realtimeCache.remove(hostId);
        chartCache.remove(hostId);
        hostListCache = null;
    }

    private static class CacheEntry<T> {
        final T value;
        final long expireAt;

        CacheEntry(T value, long ttlMillis) {
            this.value = value;
            this.expireAt = System.currentTimeMillis() + ttlMillis;
        }

        boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
