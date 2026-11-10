package com.smartfarm.controller;

import com.smartfarm.model.VideoLive;
import com.smartfarm.service.EzvizService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 视频监控接口
 */
@RestController
@RequestMapping("/video")
public class VideoController {

    private final EzvizService ezvizService;

    public VideoController(EzvizService ezvizService) {
        this.ezvizService = ezvizService;
    }

    /** 获取直播地址（http-flv）及设备状态；refresh=true 强制生成新地址 */
    @GetMapping("/live")
    public VideoLive getLive(@RequestParam(defaultValue = "false") boolean refresh) {
        return ezvizService.getLive(refresh);
    }
}
