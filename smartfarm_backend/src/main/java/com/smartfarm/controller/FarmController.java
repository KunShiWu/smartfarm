package com.smartfarm.controller;

import com.smartfarm.model.FarmAnalyzeRequest;
import com.smartfarm.model.FarmAnalyzeResult;
import com.smartfarm.service.FarmAnalyzeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import com.smartfarm.service.FarmImageService;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
/**
 * 农田分析 agent 接口
 */
@RestController
@RequestMapping("/farm")
public class FarmController {

    private final FarmAnalyzeService farmAnalyzeService;
    private final FarmImageService farmImageService;
    public FarmController(
            FarmAnalyzeService farmAnalyzeService,
            FarmImageService farmImageService
    ) {
        this.farmAnalyzeService = farmAnalyzeService;
        this.farmImageService = farmImageService;
    }

    public record ObserveByIdRequest(
            String image_id
    ) {}
    /** 结合摄像头截图与土壤数据，给出农情诊断与农事建议 */
    @PostMapping("/analyze")
    public FarmAnalyzeResult analyze(@RequestBody FarmAnalyzeRequest request) {
        return farmAnalyzeService.analyze(request);
    }
    /**
     * 农作物病害视觉观察接口
     */
    @PostMapping("/observe")
    public Map<String, Object> observe(
            @RequestBody FarmAnalyzeRequest request
    ) {
        return farmAnalyzeService.observe(request);
    }

    /**
     * 上传农作物图片，返回 image_id
     */
    @PostMapping("/images")
    public Map<String, String> uploadImage(
            @RequestParam("file") MultipartFile file
    ) {
        String imageId = farmImageService.save(file);

        return Map.of("image_id", imageId);
    }

    /**
     * 根据已上传图片的 image_id 进行病害识别
     */
    @PostMapping("/observe-by-id")
    public Map<String, Object> observeById(
            @RequestBody ObserveByIdRequest request
    ) {
        return farmAnalyzeService.observeById(
                request.image_id()
        );
    }
}
