package com.smartfarm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfarm.common.BizException;
import com.smartfarm.config.AppProperties;
import com.smartfarm.model.WeatherInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 天气服务：调用 UAPI 实时天气接口，30 分钟缓存。
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final long CACHE_TTL = 30 * 60 * 1000;

    private final AppProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile WeatherInfo cached;
    private volatile long cacheExpireAt;

    public WeatherService(AppProperties props, HostService hostService) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8_000);
        factory.setReadTimeout(8_000);
        this.restTemplate = new RestTemplate(factory);
    }

    public WeatherInfo getWeather() {
        WeatherInfo c = cached;
        if (c != null && System.currentTimeMillis() < cacheExpireAt) {
            return c;
        }
        synchronized (this) {
            if (cached != null && System.currentTimeMillis() < cacheExpireAt) {
                return cached;
            }
            cached = doFetch();
            cacheExpireAt = System.currentTimeMillis() + CACHE_TTL;
            return cached;
        }
    }

    private WeatherInfo doFetch() {
        AppProperties.Weather w = props.getWeather();
        if (!StringUtils.hasText(w.getApiKey())) {
            throw new BizException("天气 API 密钥未配置，请设置 SMARTFARM_WEATHER_API_KEY");
        }
        String baseUrl = w.getBaseUrl().replaceAll("/+$", "");
        URI url = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/api/v1/misc/weather")
                .queryParam("city", w.getDefaultCity())
                .queryParam("adcode", w.getAdcode())
                .queryParam("extended", false)
                .queryParam("forecast", false)
                .queryParam("hourly", false)
                .queryParam("minutely", false)
                .queryParam("indices", false)
                .queryParam("lang", "zh")
                .build()
                .encode()
                .toUri();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(w.getApiKey().trim());
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            return objectMapper.readValue(response.getBody(), WeatherInfo.class);
        } catch (Exception e) {
            log.warn("天气获取失败: {}", e.getMessage());
            throw new BizException("天气获取失败: " + e.getMessage(), e);
        }
    }
}
