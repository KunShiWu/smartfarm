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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 物联网开放平台（mordeniot）API 客户端。
 *
 * <p>要点：
 * <ul>
 *   <li>所有业务接口需在请求头携带 {@code Authorization: Bearer <token>}</li>
 *   <li>token 有效期约 15 天，重新获取会使旧 token 立即失效，因此全局缓存复用，
 *       临近过期才重新登录；接口返回 401 时自动重新登录并重试一次</li>
 *   <li>token 接口限流 100 次/小时，切勿频繁调用</li>
 * </ul>
 */
@Component
public class MordenIotClient {

    private static final Logger log = LoggerFactory.getLogger(MordenIotClient.class);

    /** 提前 12 小时续期 */
    private static final long REFRESH_AHEAD_MILLIS = 12L * 3600 * 1000;

    private final AppProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String token;
    private volatile long tokenExpireAtMillis;

    public MordenIotClient(AppProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);
        this.restTemplate = new RestTemplate(factory);
    }

    // ------------------------------------------------------------------
    // 业务接口
    // ------------------------------------------------------------------

    /** 自己的主机列表 */
    public JsonNode listMyDevices(int page, int perPage) {
        return getWithRetry("/device/myself_list",
                kv("page", String.valueOf(page), "per_page", String.valueOf(perPage)));
    }

    /** 主机详情 */
    public JsonNode deviceInfo(long deviceId) {
        return getWithRetry("/device/info", kv("id", String.valueOf(deviceId)));
    }

    /** 实时数据（限流 10 次/分钟） */
    public JsonNode realtimeInfo(long deviceId) {
        return getWithRetry("/real_time/info", kv("id", String.valueOf(deviceId)));
    }

    /** 历史数据（限流 30 次/分钟） */
    public JsonNode historyList(long deviceId, int page, int perPage, String startTime, String endTime) {
        return getWithRetry("/history/list", kv(
                "id", String.valueOf(deviceId),
                "page", String.valueOf(page),
                "per_page", String.valueOf(perPage),
                "start_time", startTime,
                "end_time", endTime));
    }

    /** 设置主机更新（采集）间隔，单位秒 */
    public void setUpdateSecond(long deviceId, long seconds) {
        postFormWithRetry("/real_time/set_update_second",
                kv("id", String.valueOf(deviceId), "seconds", String.valueOf(seconds)));
    }

    /** 编辑主机信息（name、time_zone_id 必填） */
    public void editDevice(long deviceId, String name, long timeZoneId) {
        postFormWithRetry("/device/edit", kv(
                "id", String.valueOf(deviceId),
                "name", name,
                "time_zone_id", String.valueOf(timeZoneId)));
    }

    /** 组装不可变查询参数表（Java 8 兼容） */
    private static Map<String, String> kv(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    // ------------------------------------------------------------------
    // token 管理
    // ------------------------------------------------------------------

    /** 获取可用 token；过期或不存在时重新登录 */
    public String getToken() {
        String t = token;
        if (t == null || System.currentTimeMillis() > tokenExpireAtMillis - REFRESH_AHEAD_MILLIS) {
            synchronized (this) {
                t = token;
                if (t == null || System.currentTimeMillis() > tokenExpireAtMillis - REFRESH_AHEAD_MILLIS) {
                    login();
                    t = token;
                }
            }
        }
        return t;
    }

    private void login() {
        AppProperties.Upstream up = props.getUpstream();
        log.info("登录开放平台获取 token, account={}", up.getAccount());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("account", up.getAccount());
        form.add("password", up.getPassword());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        JsonNode resp = exchange(URI.create(up.getBaseUrl() + "/login/account_login"),
                HttpMethod.POST, new HttpEntity<>(form, headers));

        JsonNode data = checkCode(resp);
        this.token = data.path("token").asText();
        this.tokenExpireAtMillis = data.path("expire_time").asLong() * 1000L;
        log.info("token 获取成功, 有效期至 {}", this.tokenExpireAtMillis);
    }

    /** 使当前 token 失效（用于 401 后重新登录） */
    private void invalidateToken() {
        synchronized (this) {
            this.token = null;
            this.tokenExpireAtMillis = 0L;
        }
    }

    // ------------------------------------------------------------------
    // 请求封装（401 自动重登并重试一次）
    // ------------------------------------------------------------------

    private JsonNode getWithRetry(String path, Map<String, String> query) {
        JsonNode resp = doGet(path, query);
        if (isUnauthorized(resp)) {
            log.info("token 失效(401), 重新登录后重试: {}", path);
            invalidateToken();
            resp = doGet(path, query);
        }
        return checkCode(resp);
    }

    private JsonNode postFormWithRetry(String path, Map<String, String> form) {
        JsonNode resp = doPostForm(path, form);
        if (isUnauthorized(resp)) {
            log.info("token 失效(401), 重新登录后重试: {}", path);
            invalidateToken();
            resp = doPostForm(path, form);
        }
        return checkCode(resp);
    }

    private JsonNode doGet(String path, Map<String, String> query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(props.getUpstream().getBaseUrl() + path);
        query.forEach(builder::queryParam);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + getToken());

        return exchange(builder.build().encode().toUri(), HttpMethod.GET, new HttpEntity<>(headers));
    }

    private JsonNode doPostForm(String path, Map<String, String> form) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        form.forEach(body::add);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + getToken());

        return exchange(URI.create(props.getUpstream().getBaseUrl() + path), HttpMethod.POST, new HttpEntity<>(body, headers));
    }

    private JsonNode exchange(URI url, HttpMethod method, HttpEntity<?> entity) {
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, method, entity, String.class);
            return objectMapper.readTree(resp.getBody());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("调用上游平台失败: " + e.getMessage(), e);
        }
    }

    private boolean isUnauthorized(JsonNode resp) {
        return resp != null && resp.path("code").asInt() == 401;
    }

    /** 校验上游响应 code，成功返回 data 节点，失败抛业务异常 */
    private JsonNode checkCode(JsonNode resp) {
        if (resp == null) {
            throw new BizException("上游平台无响应");
        }
        int code = resp.path("code").asInt(-1);
        if (code == 200) {
            return resp.path("data");
        }
        throw new BizException("上游平台错误(" + code + "): " + resp.path("mess").asText());
    }
}
