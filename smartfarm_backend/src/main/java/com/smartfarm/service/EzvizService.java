package com.smartfarm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfarm.common.BizException;
import com.smartfarm.config.AppProperties;
import com.smartfarm.model.VideoLive;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * 萤石开放平台客户端：Token 管理 + 直播地址获取
 * <p>
 * 接口文档：
 * - POST /api/lapp/token/get            获取 accessToken（有效期约 7 天，提前 1 小时续期）
 * - POST /api/lapp/v2/live/address/get  获取直播地址（protocol=4 即 http-flv，HLS 对电池相机不可用）
 * - POST /api/lapp/device/info          设备信息（在线状态/信号强度）
 */
@Service
public class EzvizService {

    /** Token 错误码（token 异常/过期），触发重取后重试一次 */
    private static final String CODE_TOKEN_ERROR = "10002";
    /** 直播地址缓存时长（地址本身 24h 有效，缓存 5 分钟足够避免频繁生成） */
    private static final long LIVE_CACHE_MS = 5 * 60 * 1000;
    /** 提前续期窗口 */
    private static final long TOKEN_REFRESH_AHEAD_MS = 60 * 60 * 1000;

    private final AppProperties props;
    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private String accessToken;
    private long tokenExpireAt;
    private VideoLive liveCache;
    private long liveCacheAt;

    public EzvizService(AppProperties props) {
        this.props = props;
    }

    /** 获取直播信息（带 5 分钟缓存；refresh=true 时强制生成新地址，用于电池相机唤醒重试） */
    public synchronized VideoLive getLive(boolean refresh) {
        if (refresh) {
            liveCache = null;
        }
        if (liveCache != null && System.currentTimeMillis() - liveCacheAt < LIVE_CACHE_MS) {
            return liveCache;
        }
        VideoLive live = fetchLive();
        liveCache = live;
        liveCacheAt = System.currentTimeMillis();
        return live;
    }

    private VideoLive fetchLive() {
        AppProperties.Ezviz cfg = props.getEzviz();

        JsonNode liveData = postWithRetry("/api/lapp/v2/live/address/get",
                form("deviceSerial", cfg.getDeviceSerial(),
                        "channelNo", String.valueOf(cfg.getChannelNo()),
                        "protocol", "4"));

        VideoLive live = new VideoLive();
        live.url = liveData.path("url").asText();
        live.expireTime = liveData.path("expireTime").asText(null);
        // EZUIKit 官方播放器所需：accessToken + ezopen 地址
        live.accessToken = getToken();
        live.ezopenUrl = "ezopen://open.ys7.com/" + cfg.getDeviceSerial() + "/"
                + cfg.getChannelNo() + ".hd.live";

        // 设备在线状态/信号（失败不影响直播地址返回）
        try {
            JsonNode info = postWithRetry("/api/lapp/device/info",
                    form("deviceSerial", cfg.getDeviceSerial()));
            live.online = info.path("status").asInt() == 1;
            live.deviceName = info.path("deviceName").asText(null);
            live.signal = info.path("signal").asText(null);
        } catch (Exception ignored) {
            live.online = null;
        }
        return live;
    }

    /**
     * 仅获取设备状态（名称/在线/信号），不请求直播地址。
     *
     * <p>设备离线时 {@link #getLive(boolean)} 会因「设备不在线」而出错，
     * 但设备信息接口仍然可用，因此设备列表用它来判断在线状态。
     */
    public VideoLive getDeviceStatus() {
        AppProperties.Ezviz cfg = props.getEzviz();
        JsonNode info = postWithRetry("/api/lapp/device/info",
                form("deviceSerial", cfg.getDeviceSerial()));

        VideoLive status = new VideoLive();
        status.online = info.path("status").asInt() == 1;
        status.deviceName = info.path("deviceName").asText(null);
        status.signal = info.path("signal").asText(null);
        return status;
    }

    /** 带 token 的表单请求，遇 token 失效自动重取并重试一次 */
    private JsonNode postWithRetry(String path, MultiValueMap<String, String> form) {
        try {
            return doPost(path, form);
        } catch (BizException e) {
            if (e.getMessage() != null && e.getMessage().contains(CODE_TOKEN_ERROR)) {
                synchronized (this) {
                    accessToken = null;
                }
                return doPost(path, form);
            }
            throw e;
        }
    }

    private JsonNode doPost(String path, MultiValueMap<String, String> form) {
        form.add("accessToken", getToken());
        String url = props.getEzviz().getBaseUrl() + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> resp = rest.postForEntity(url, new HttpEntity<>(form, headers), String.class);
        try {
            JsonNode root = mapper.readTree(resp.getBody());
            String code = root.path("code").asText();
            if (!"200".equals(code)) {
                throw new BizException("萤石接口错误(code=" + code + "): " + root.path("msg").asText());
            }
            return root.path("data");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("萤石接口响应解析失败", e);
        }
    }

    /** 获取 accessToken（缓存，提前 1 小时续期） */
    private synchronized String getToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireAt - TOKEN_REFRESH_AHEAD_MS) {
            return accessToken;
        }
        AppProperties.Ezviz cfg = props.getEzviz();
        String url = cfg.getBaseUrl() + "/api/lapp/token/get";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = form("appKey", cfg.getAppKey(), "appSecret", cfg.getAppSecret());
        try {
            ResponseEntity<String> resp = rest.postForEntity(url, new HttpEntity<>(form, headers), String.class);
            JsonNode root = mapper.readTree(resp.getBody());
            if (!"200".equals(root.path("code").asText())) {
                throw new BizException("萤石获取Token失败: " + root.path("msg").asText());
            }
            JsonNode data = root.path("data");
            accessToken = data.path("accessToken").asText();
            tokenExpireAt = data.path("expireTime").asLong();
            return accessToken;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("萤石获取Token失败", e);
        }
    }

    private static MultiValueMap<String, String> form(String... kv) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.add(kv[i], kv[i + 1]);
        }
        return map;
    }
}
