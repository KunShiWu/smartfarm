package com.smartfarm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.smartfarm.client.MijiaBridgeClient;
import com.smartfarm.common.BizException;
import com.smartfarm.config.AppProperties;
import com.smartfarm.model.IrrigationState;
import com.smartfarm.model.SoilReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 喷灌控制服务（水泵开关）。
 *
 * <p>规则：
 * <ul>
 *   <li>自动模式：土壤湿度低于阈值 → 开启喷灌；达到/超过阈值 → 自动关闭</li>
 *   <li>时长保护：单次喷灌达到设定时长后自动关闭，避免一直喷</li>
 *   <li>手动模式：仅由手动开关控制，自动逻辑不干预</li>
 * </ul>
 * 阈值、喷灌时长、模式持久化在 data/irrigation.json。
 */
@Service
public class IrrigationService {

    private static final Logger log = LoggerFactory.getLogger(IrrigationService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 允许的模式取值 */
    private static final String MODE_AUTO = "auto";
    private static final String MODE_MANUAL = "manual";

    private final AppProperties props;
    private final MijiaBridgeClient bridge;
    private final HostService hostService;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** 阈值/时长/模式（持久化） */
    private volatile Config config = new Config();
    /** 水泵开关（运行态） */
    private volatile boolean on = false;
    /** 本次开启时刻 */
    private volatile long onAtMillis = 0L;
    /** 时长到点关闭后的冷却截止时刻，避免湿度仍偏低时立刻反复启停 */
    private volatile long cooldownUntilMillis = 0L;

    private volatile String lastAction = "";
    private volatile String lastActionAt = "";
    private volatile String lastActionSource = "";

    private Path storeFile;

    public IrrigationService(AppProperties props, MijiaBridgeClient bridge, HostService hostService) {
        this.props = props;
        this.bridge = bridge;
        this.hostService = hostService;
    }

    @PostConstruct
    public void init() {
        storeFile = Paths.get(props.getDataDir()).resolve("irrigation.json");
        if (Files.exists(storeFile)) {
            try {
                config = mapper.readValue(storeFile.toFile(), Config.class);
                log.info("加载喷灌配置: 阈值={}% 时长={}分钟 模式={}",
                        config.threshold, config.durationMinutes, config.mode);
            } catch (IOException e) {
                log.warn("喷灌配置文件读取失败，使用默认配置: {}", e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // 查询与手动操作
    // ------------------------------------------------------------------

    /** 当前状态（含最近土壤湿度）；读取时先与设备真实开关同步，保证页面与实际一致 */
    public IrrigationState getState() {
        reconcileFromDevice();
        SoilReading soil = hostService.readSoilMoisture();
        return buildState(soil);
    }

    /** 手动开关（会切到手动模式，避免自动逻辑立刻把它改回去） */
    public IrrigationState switchManually(boolean target) {
        bridge.setSwitch(target);
        this.on = target;
        this.onAtMillis = target ? System.currentTimeMillis() : 0L;
        this.cooldownUntilMillis = 0L;
        this.config.mode = MODE_MANUAL;
        persist();
        record(target ? "手动开启喷灌" : "手动关闭喷灌", "manual");
        return buildState(hostService.readSoilMoisture());
    }

    /** 修改阈值 / 喷灌时长 / 控制模式 */
    public IrrigationState updateConfig(Double threshold, Integer durationMinutes, String mode) {
        if (threshold != null) {
            if (threshold <= 0 || threshold >= 100) {
                throw new BizException("土壤湿度阈值应在 0~100 之间");
            }
            config.threshold = threshold;
        }
        if (durationMinutes != null) {
            if (durationMinutes < 1 || durationMinutes > 180) {
                throw new BizException("喷灌时长应在 1~180 分钟之间");
            }
            config.durationMinutes = durationMinutes;
        }
        if (mode != null && !mode.trim().isEmpty()) {
            String m = mode.trim().toLowerCase();
            if (!MODE_AUTO.equals(m) && !MODE_MANUAL.equals(m)) {
                throw new BizException("控制模式只能是 auto 或 manual");
            }
            config.mode = m;
            // 切回自动模式时清掉冷却，立即参与判断
            if (MODE_AUTO.equals(m)) {
                cooldownUntilMillis = 0L;
            }
        }
        persist();
        record("修改喷灌设置: 阈值" + fmt(config.threshold) + "% 时长" + config.durationMinutes
                + "分钟 模式" + ("auto".equals(config.mode) ? "自动" : "手动"), "manual");
        return buildState(hostService.readSoilMoisture());
    }

    /** 桥接服务是否可达（设备列表在线状态） */
    public boolean isBridgeReachable() {
        return bridge.isReachable();
    }

    // ------------------------------------------------------------------
    // 自动控制
    // ------------------------------------------------------------------

    /**
     * 自动控制轮询：同步设备真实开关状态 → 时长保护 → 阈值判断。
     * 湿度读不到（上游异常/离线）时不动作，避免误判。
     */
    @Scheduled(fixedDelay = 20_000L, initialDelay = 10_000L)
    public void autoCheck() {
        long now = System.currentTimeMillis();
        reconcileFromDevice();

        // 1. 时长到点，自动关闭（任何模式都生效，属于安全保护）
        if (on && config.durationMinutes > 0
                && now - onAtMillis >= config.durationMinutes * 60_000L) {
            if (turnOff("auto", "喷灌 " + config.durationMinutes + " 分钟已到，自动关闭")) {
                cooldownUntilMillis = now + config.durationMinutes * 60_000L;
            }
            return;
        }

        // 2. 手动模式不参与自动判断
        if (!MODE_AUTO.equals(config.mode)) {
            return;
        }
        if (now < cooldownUntilMillis) {
            return;
        }

        // 3. 按土壤湿度阈值判断（湿度直接取自传感器实时数据的「土壤湿度」通道）
        SoilReading soil = hostService.readSoilMoisture();
        if (soil == null || soil.moisture == null) {
            // 通道缺失/无数值：属于真正读不到数据，跳过，避免误判
            return;
        }
        double threshold = config.threshold;
        if (soil.moisture < threshold && !on) {
            turnOn("auto", "土壤湿度 " + fmt(soil.moisture) + "% 低于阈值 " + fmt(threshold) + "%，自动开启喷灌");
        } else if (soil.moisture >= threshold && on) {
            turnOff("auto", "土壤湿度 " + fmt(soil.moisture) + "% 已达到阈值 " + fmt(threshold) + "%，自动关闭喷灌");
        }
    }

    private boolean turnOn(String source, String action) {
        try {
            bridge.setSwitch(true);
            on = true;
            onAtMillis = System.currentTimeMillis();
            record(action, source);
            log.info("自动开启喷灌: {}", action);
            return true;
        } catch (Exception e) {
            log.warn("自动开启喷灌失败: {}", e.getMessage());
            record("自动开启喷灌失败: " + e.getMessage(), source);
            return false;
        }
    }

    private boolean turnOff(String source, String action) {
        try {
            bridge.setSwitch(false);
            on = false;
            onAtMillis = 0L;
            record(action, source);
            log.info("自动关闭喷灌: {}", action);
            return true;
        } catch (Exception e) {
            log.warn("自动关闭喷灌失败: {}", e.getMessage());
            record("自动关闭喷灌失败: " + e.getMessage(), source);
            return false;
        }
    }

    /** 用设备真实状态校正本地记录（重启后 / 被其它方式控制时保持一致） */
    private void reconcileFromDevice() {
        if (!bridge.isConfigured()) {
            return;
        }
        try {
            Boolean deviceOn = bridge.getSwitch();
            if (deviceOn == null || deviceOn == on) {
                return;
            }
            boolean wasOn = on;
            on = deviceOn;
            if (on && !wasOn) {
                onAtMillis = System.currentTimeMillis();
            }
            if (!on) {
                onAtMillis = 0L;
            }
            record("同步设备状态：" + (on ? "开启" : "关闭"), "device");
        } catch (Exception e) {
            log.debug("读取水泵开关状态失败: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private IrrigationState buildState(SoilReading soil) {
        IrrigationState state = new IrrigationState();
        state.online = bridge.isReachable();
        state.bridgeConfigured = bridge.isConfigured();
        state.on = on;
        state.mode = config.mode;
        state.threshold = config.threshold;
        state.durationMinutes = config.durationMinutes;
        state.soilMoisture = soil == null ? null : soil.moisture;
        state.soilHostName = soil == null ? null : soil.hostName;
        state.soilCollectTime = soil == null ? null : soil.collectTime;
        state.remainingSeconds = remainingSeconds();
        state.lastAction = lastAction;
        state.lastActionAt = lastActionAt;
        state.lastActionSource = lastActionSource;
        state.updatedAt = LocalDateTime.now().format(FMT);
        return state;
    }

    /** 本次喷灌剩余秒数（未开启或未设时长时为 null） */
    private Long remainingSeconds() {
        if (!on || onAtMillis <= 0 || config.durationMinutes <= 0) {
            return null;
        }
        long limit = config.durationMinutes * 60L;
        long used = (System.currentTimeMillis() - onAtMillis) / 1000L;
        return Math.max(0L, limit - used);
    }

    private void record(String action, String source) {
        this.lastAction = action;
        this.lastActionAt = LocalDateTime.now().format(FMT);
        this.lastActionSource = source;
    }

    private void persist() {
        try {
            Files.createDirectories(storeFile.getParent());
            mapper.writeValue(storeFile.toFile(), config);
        } catch (IOException e) {
            log.error("喷灌配置持久化失败: {}", e.getMessage());
        }
    }

    private static String fmt(Double v) {
        return v == null ? "-" : String.valueOf(Math.round(v * 10) / 10.0);
    }

    /** 持久化配置 */
    public static class Config {
        /** 土壤湿度阈值（%）：低于该值自动开启喷灌，达到后自动关闭 */
        public double threshold = 30.0;
        /** 单次喷灌时长（分钟） */
        public int durationMinutes = 5;
        /** auto=自动，manual=手动 */
        public String mode = MODE_AUTO;
    }
}
