package com.smartfarm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfarm.common.BizException;
import com.smartfarm.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 米家桥接服务客户端（云服务器上的 app.py，FastAPI 实现，经 SSH 隧道本地访问）。
 *
 * <p>接口约定：
 * <ul>
 *   <li>{@code GET /health} 服务存活（无需鉴权）</li>
 *   <li>{@code GET /devices/{deviceId}/status} 读取设备开关（需 {@code X-Bridge-Token}），返回 {@code {"on": true|false}}</li>
 *   <li>{@code POST /devices/{deviceId}/switch} 控制设备开关，请求体 {@code {"on": true|false}}（需 {@code X-Bridge-Token}）</li>
 * </ul>
 */
@Component
public class MijiaBridgeClient {

    private static final Logger log = LoggerFactory.getLogger(MijiaBridgeClient.class);

    private final AppProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MijiaBridgeClient(AppProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(Math.max(3, props.getMijia().getRequestTimeoutSeconds()) * 1000);
        this.restTemplate = new RestTemplate(factory);
    }

    /** 是否已配置 Token（未配置时无法控制开关） */
    public boolean isConfigured() {
        String token = props.getMijia().getBridge().getToken();
        return token != null && !token.trim().isEmpty();
    }

    /** 桥接服务是否可达（/health 不需要鉴权，用于设备在线状态） */
    public boolean isReachable() {
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(
                    props.getMijia().getBridge().getBaseUrl() + "/health", String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("米家桥接服务不可达: {}", e.getMessage());
            return false;
        }
    }

    /** 读取设备开关状态；返回内容不是布尔值时抛出业务异常 */
    public Boolean getSwitch() {
        requireToken();
        JsonNode resp = call(HttpMethod.GET, devicePath("/status"), new HttpEntity<>(headers()));
        JsonNode onNode = resp.path("on");
        if (!onNode.isBoolean()) {
            throw new BizException("米家桥接服务返回的开关状态无效: " + resp);
        }
        return onNode.booleanValue();
    }

    /** 控制设备开关 */
    public void setSwitch(boolean on) {
        requireToken();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("on", on);

        HttpHeaders headers = headers();
        headers.setContentType(MediaType.APPLICATION_JSON);
        JsonNode resp = call(HttpMethod.POST, devicePath("/switch"), new HttpEntity<>(body, headers));

        // 桥接服务以 command_accepted 表示指令已被米家云端接受
        if (!resp.path("command_accepted").asBoolean(false)) {
            throw new BizException("水泵开关指令未被米家云端接受: " + resp);
        }
        log.info("米家水泵开关已下发: on={}", on);
    }

    /** 拼接设备相关接口路径，设备 id 取自配置（app.py 中 DEVICES 的键名） */
    private String devicePath(String suffix) {
        return "/devices/" + props.getMijia().getBridge().getDeviceId() + suffix;
    }

    private void requireToken() {
        if (!isConfigured()) {
            throw new BizException("未配置米家桥接服务 Token，无法控制水泵开关（请设置环境变量 MIJIA_BRIDGE_TOKEN）");
        }
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Bridge-Token", props.getMijia().getBridge().getToken().trim());
        return headers;
    }

    private JsonNode call(HttpMethod method, String path, HttpEntity<?> entity) {
        String url = props.getMijia().getBridge().getBaseUrl() + path;
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, method, entity, String.class);
            return objectMapper.readTree(resp.getBody());
        } catch (RestClientResponseException e) {
            int status = e.getRawStatusCode();
            if (status == 403 || status == 401) {
                throw new BizException("米家桥接服务鉴权失败，请检查 MIJIA_BRIDGE_TOKEN 是否与桥接服务一致");
            }
            throw new BizException("米家桥接服务返回错误(" + status + "): " + e.getResponseBodyAsString());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("米家桥接服务不可达: " + e.getMessage(), e);
        }
    }
}
