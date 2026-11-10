package com.smartfarm.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeatherInfoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsCurrentConditionsAndAlertFromUapiResponse() throws Exception {
        String json = "{"
                + "\"province\":\"福建省\","
                + "\"city\":\"厦门市\","
                + "\"district\":\"翔安区\","
                + "\"weather\":\"多云\","
                + "\"temperature\":24,"
                + "\"wind_direction\":\"东风\","
                + "\"wind_power\":\"4级\","
                + "\"report_time\":\"5 分钟前发布\","
                + "\"temp_min\":23,"
                + "\"temp_max\":29,"
                + "\"humidity\":63,"
                + "\"unknown_future_field\":true,"
                + "\"alerts\":[{"
                + "\"title\":\"大风黄色预警\","
                + "\"type\":\"大风\","
                + "\"level\":\"黄色\","
                + "\"text\":\"预计未来12小时风力增强。\","
                + "\"publish_time\":\"2026-10-21 23:30:00\","
                + "\"publisher\":\"翔安区气象台\","
                + "\"guidance\":[\"小心坠物\",\"远离树木\"]"
                + "}]}";

        WeatherInfo result = objectMapper.readValue(json, WeatherInfo.class);

        assertEquals("福建省", result.province);
        assertEquals("厦门市", result.city);
        assertEquals("翔安区", result.district);
        assertEquals("多云", result.weather);
        assertEquals("多云", result.text);
        assertEquals(24, result.temperature);
        assertEquals("东风", result.windDirection);
        assertEquals("4级", result.windPower);
        assertEquals(23, result.low);
        assertEquals(29, result.high);
        assertEquals(63, result.humidity);
        assertEquals(1, result.alerts.size());
        assertEquals("大风黄色预警", result.alerts.get(0).title);
        assertEquals("黄色", result.alerts.get(0).level);
        assertEquals(2, result.alerts.get(0).guidance.size());

        JsonNode frontendJson = objectMapper.valueToTree(result);
        assertEquals("东风", frontendJson.path("windDirection").asText());
        assertEquals("2026-10-21 23:30:00", frontendJson.path("alerts").path(0).path("publishTime").asText());
    }
}
