package com.smartfarm.service;

import com.smartfarm.common.BizException;
import com.smartfarm.config.AppProperties;
import com.smartfarm.model.WeatherInfo;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WeatherServiceTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void callsUapiWeatherWithBearerTokenAndConfiguredDistrict() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestPath = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/misc/weather", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestPath.set(exchange.getRequestURI().toString());
            byte[] body = ("{\"province\":\"福建省\",\"city\":\"厦门市\","
                    + "\"district\":\"翔安区\",\"weather\":\"多云\","
                    + "\"temperature\":24,\"wind_direction\":\"东风\","
                    + "\"wind_power\":\"4级\",\"alerts\":[]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        AppProperties props = new AppProperties();
        AppProperties.Weather weather = props.getWeather();
        weather.setDefaultCity("翔安区");
        ReflectionTestUtils.setField(weather, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
        ReflectionTestUtils.setField(weather, "apiKey", "weather-secret");
        ReflectionTestUtils.setField(weather, "adcode", "350213");

        WeatherInfo result = new WeatherService(props, mock(HostService.class)).getWeather();

        assertEquals("Bearer weather-secret", authorization.get());
        assertTrue(requestPath.get().startsWith("/api/v1/misc/weather?"));
        assertTrue(requestPath.get().contains("city=%E7%BF%94%E5%AE%89%E5%8C%BA"));
        assertTrue(requestPath.get().contains("adcode=350213"));
        assertTrue(requestPath.get().contains("lang=zh"));
        assertEquals("翔安区", result.district);
        assertEquals(24, result.temperature);
    }

    @Test
    void rejectsMissingApiKeyBeforeCallingWeatherProvider() {
        AppProperties props = new AppProperties();
        ReflectionTestUtils.setField(props.getWeather(), "baseUrl", "http://127.0.0.1:1");
        ReflectionTestUtils.setField(props.getWeather(), "apiKey", "  ");

        BizException error = assertThrows(
                BizException.class,
                () -> new WeatherService(props, mock(HostService.class)).getWeather()
        );

        assertEquals("天气 API 密钥未配置，请设置 SMARTFARM_WEATHER_API_KEY", error.getMessage());
    }
}
