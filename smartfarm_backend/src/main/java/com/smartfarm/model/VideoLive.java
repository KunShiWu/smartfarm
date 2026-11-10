package com.smartfarm.model;

/**
 * 视频直播信息（前端直接播放 url，协议 http-flv）
 */
public class VideoLive {

    /** 直播流地址（http-flv，可直接给 flv.js 播放） */
    public String url;
    /** 协议标识，固定 flv */
    public String protocol = "flv";
    /** 地址过期时间 */
    public String expireTime;
    /** 设备是否在线 */
    public Boolean online;
    /** 设备名称 */
    public String deviceName;
    /** 网络信号强度，如 84% */
    public String signal;
    /** 萤石 accessToken（供前端 EZUIKit 播放器使用） */
    public String accessToken;
    /** ezopen 播放地址（EZUIKit 播放器使用） */
    public String ezopenUrl;
}
