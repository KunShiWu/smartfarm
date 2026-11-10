package com.smartfarm.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 天气信息
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeatherInfo {

    public String province;
    public String city;
    public String district;
    public String weather;
    public String text;
    public Integer temperature;
    @JsonAlias("wind_direction")
    public String windDirection;
    @JsonAlias("wind_power")
    public String windPower;
    public Integer humidity;
    @JsonAlias("report_time")
    public String reportTime;
    @JsonAlias("temp_min")
    public Integer low;
    @JsonAlias("temp_max")
    public Integer high;
    public List<WeatherAlert> alerts = new ArrayList<>();

    public WeatherInfo() {
    }

    public WeatherInfo(String city, String text, Integer low, Integer high) {
        this.city = city;
        this.weather = text;
        this.text = text;
        this.low = low;
        this.high = high;
    }

    public void setWeather(String weather) {
        this.weather = weather;
        this.text = weather;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WeatherAlert {
        public String title;
        public String type;
        public String level;
        public String text;
        @JsonAlias("publish_time")
        public String publishTime;
        public String publisher;
        public List<String> guidance = new ArrayList<>();
    }
}
