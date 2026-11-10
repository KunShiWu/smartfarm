package com.smartfarm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用配置项，前缀 smartfarm
 */
@ConfigurationProperties(prefix = "smartfarm")
public class AppProperties {

    /** 上游开放平台配置 */
    private Upstream upstream = new Upstream();
    /** 前端展示用主机分组名 */
    private String hostGroup = "土壤检测";
    /** 修改记录操作人 */
    private String operator = "admin";
    /** 曲线图历史数据点数 */
    private int chartPoints = 48;
    /** 本地数据目录 */
    private String dataDir = "./data";
    /** 天气兜底配置 */
    private Weather weather = new Weather();
    /** 萤石开放平台配置 */
    private Ezviz ezviz = new Ezviz();
    /** DeepSeek 大模型配置（农田分析） */
    private DeepSeek deepSeek = new DeepSeek();
    /** 米家桥接服务配置（喷灌水泵开关控制） */
    private Mijia mijia = new Mijia();

    public Upstream getUpstream() {
        return upstream;
    }

    public void setUpstream(Upstream upstream) {
        this.upstream = upstream;
    }

    public String getHostGroup() {
        return hostGroup;
    }

    public void setHostGroup(String hostGroup) {
        this.hostGroup = hostGroup;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public int getChartPoints() {
        return chartPoints;
    }

    public void setChartPoints(int chartPoints) {
        this.chartPoints = chartPoints;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public Weather getWeather() {
        return weather;
    }

    public void setWeather(Weather weather) {
        this.weather = weather;
    }

    public Ezviz getEzviz() {
        return ezviz;
    }

    public void setEzviz(Ezviz ezviz) {
        this.ezviz = ezviz;
    }

    public DeepSeek getDeepSeek() {
        return deepSeek;
    }

    public void setDeepSeek(DeepSeek deepSeek) {
        this.deepSeek = deepSeek;
    }

    public Mijia getMijia() {
        return mijia;
    }

    public void setMijia(Mijia mijia) {
        this.mijia = mijia;
    }

    /**
     * 米家桥接服务（app.py + FastAPI，部署在云服务器，经 SSH 隧道以本地端口访问）：
     * GET /devices/{deviceId}/status 读取开关，POST /devices/{deviceId}/switch 控制开关，
     * 均需 X-Bridge-Token 请求头。
     */
    public static class Mijia {
        /** 桥接服务连接配置 */
        private Bridge bridge = new Bridge();
        /** 单次请求超时（秒） */
        private int requestTimeoutSeconds = 40;

        public Bridge getBridge() {
            return bridge;
        }

        public void setBridge(Bridge bridge) {
            this.bridge = bridge;
        }

        public int getRequestTimeoutSeconds() {
            return requestTimeoutSeconds;
        }

        public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
        }
    }

    public static class Bridge {
        /** 桥接服务地址（SSH 隧道本地入口） */
        private String baseUrl = "http://127.0.0.1:18083";
        /** 鉴权 Token，需与云服务器上桥接服务进程的 MIJIA_BRIDGE_TOKEN 环境变量一致 */
        private String token;
        /** 受控设备 id，对应 app.py 中 DEVICES 的键名 */
        private String deviceId = "outdoor_pump";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }
    }

    public static class DeepSeek {
        /** OpenAI 兼容接口基础地址 */
        private String baseUrl = "https://api.deepseek.com";
        /** API Key */
        private String apiKey;
        /** 支持图片输入的视觉模型（负责读取监控截图） */
        private String visionModel = "deepseek-flash";
        /** 文本推理模型（deepseek-v4-pro 为纯文本模型，不支持图片） */
        private String reasonModel = "deepseek-v4-pro";
        /** 单次请求超时（秒） */
        private int timeoutSeconds = 120;
        /** 推理强度：low/high/max，越低越快（用于缩短等待时间） */
        private String reasoningEffort = "low";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getVisionModel() {
            return visionModel;
        }

        public void setVisionModel(String visionModel) {
            this.visionModel = visionModel;
        }

        public String getReasonModel() {
            return reasonModel;
        }

        public void setReasonModel(String reasonModel) {
            this.reasonModel = reasonModel;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public String getReasoningEffort() {
            return reasoningEffort;
        }

        public void setReasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
        }
    }

    public static class Upstream {
        /** 开放平台基础地址 */
        private String baseUrl = "https://openapi.mordeniot.com/openapi/v1";
        /** 开放平台账号 */
        private String account;
        /** 开放平台密码 */
        private String password;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getAccount() {
            return account;
        }

        public void setAccount(String account) {
            this.account = account;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Weather {
        private String baseUrl = "https://uapis.cn";
        private String apiKey;
        private String adcode = "350213";
        private String defaultCity = "翔安区";
        private double defaultLatitude = 24.6109;
        private double defaultLongitude = 118.2953;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getAdcode() {
            return adcode;
        }

        public void setAdcode(String adcode) {
            this.adcode = adcode;
        }

        public String getDefaultCity() {
            return defaultCity;
        }

        public void setDefaultCity(String defaultCity) {
            this.defaultCity = defaultCity;
        }

        public double getDefaultLatitude() {
            return defaultLatitude;
        }

        public void setDefaultLatitude(double defaultLatitude) {
            this.defaultLatitude = defaultLatitude;
        }

        public double getDefaultLongitude() {
            return defaultLongitude;
        }

        public void setDefaultLongitude(double defaultLongitude) {
            this.defaultLongitude = defaultLongitude;
        }
    }

    public static class Ezviz {
        /** 萤石开放平台基础地址 */
        private String baseUrl = "https://open.ys7.com";
        private String appKey;
        private String appSecret;
        /** 设备序列号 */
        private String deviceSerial;
        /** 通道号 */
        private int channelNo = 1;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getAppKey() {
            return appKey;
        }

        public void setAppKey(String appKey) {
            this.appKey = appKey;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getDeviceSerial() {
            return deviceSerial;
        }

        public void setDeviceSerial(String deviceSerial) {
            this.deviceSerial = deviceSerial;
        }

        public int getChannelNo() {
            return channelNo;
        }

        public void setChannelNo(int channelNo) {
            this.channelNo = channelNo;
        }
    }
}
