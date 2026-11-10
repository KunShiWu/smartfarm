package com.smartfarm.controller;

import com.smartfarm.common.BizException;
import com.smartfarm.model.IrrigationState;
import com.smartfarm.service.IrrigationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 喷灌设备接口：与前端 src/api/index.js 约定结构一一对应
 */
@RestController
@RequestMapping("/irrigation")
public class IrrigationController {

    private final IrrigationService irrigationService;

    public IrrigationController(IrrigationService irrigationService) {
        this.irrigationService = irrigationService;
    }

    /** 喷灌设备状态（开关、阈值、模式、最近土壤湿度、剩余时长） */
    @GetMapping
    public IrrigationState getState() {
        return irrigationService.getState();
    }

    /** 手动开关水泵 */
    @PostMapping("/switch")
    public IrrigationState switchPump(@RequestBody Map<String, Object> body) {
        Object value = body.get("on");
        if (!(value instanceof Boolean)) {
            throw new BizException("缺少参数: on（true/false）");
        }
        return irrigationService.switchManually((Boolean) value);
    }

    /** 修改阈值 / 喷灌时长 / 控制模式 */
    @PutMapping("/config")
    public IrrigationState updateConfig(@RequestBody Map<String, Object> body) {
        return irrigationService.updateConfig(
                toDouble(body.get("threshold")),
                toInteger(body.get("durationMinutes")),
                body.get("mode") == null ? null : String.valueOf(body.get("mode")));
    }

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        throw new BizException("threshold 必须是数字");
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw new BizException("durationMinutes 必须是数字");
    }
}
