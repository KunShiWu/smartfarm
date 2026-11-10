package com.smartfarm.controller;

import com.smartfarm.common.BizException;
import com.smartfarm.model.ChartData;
import com.smartfarm.model.HistoryData;
import com.smartfarm.model.HostDetail;
import com.smartfarm.model.HostSummary;
import com.smartfarm.model.ModifyRecord;
import com.smartfarm.service.HostService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 主机接口：与前端 src/api/index.js 约定结构一一对应
 */
@RestController
@RequestMapping("/hosts")
public class HostController {

    private final HostService hostService;

    public HostController(HostService hostService) {
        this.hostService = hostService;
    }

    /** 主机列表 */
    @GetMapping
    public List<HostSummary> listHosts() {
        return hostService.listHosts();
    }

    /** 主机实时参数 */
    @GetMapping("/{hostId}/realtime")
    public HostDetail getRealtime(@PathVariable String hostId) {
        return hostService.getRealtime(parseId(hostId));
    }

    /** 实时曲线数据 */
    @GetMapping("/{hostId}/realtime/chart")
    public ChartData getChart(@PathVariable String hostId) {
        return hostService.getChart(parseId(hostId));
    }

    /** 历史数据（分页表格），时间格式 yyyy-MM-dd HH:mm:ss */
    @GetMapping("/{hostId}/history")
    public HistoryData getHistory(@PathVariable String hostId,
                                  @RequestParam String startTime,
                                  @RequestParam String endTime,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "10") int perPage) {
        return hostService.getHistory(parseId(hostId), startTime, endTime, page, perPage);
    }

    /** 历史曲线（时间升序，跨页抽样 + 降采样） */
    @GetMapping("/{hostId}/history/chart")
    public ChartData getHistoryChart(@PathVariable String hostId,
                                     @RequestParam String startTime,
                                     @RequestParam String endTime,
                                     @RequestParam(defaultValue = "100") int points) {
        return hostService.getHistoryChart(parseId(hostId), startTime, endTime, points);
    }

    /** 修改采集间隔（分钟） */
    @PutMapping("/{hostId}/interval")
    public Map<String, Boolean> updateInterval(@PathVariable String hostId,
                                               @RequestBody Map<String, Integer> body) {
        Integer interval = body.get("interval");
        if (interval == null) {
            throw new BizException("缺少参数: interval");
        }
        hostService.updateInterval(parseId(hostId), interval);
        return Collections.singletonMap("success", true);
    }

    /** 修改记录 */
    @GetMapping("/{hostId}/modify-records")
    public List<ModifyRecord> getModifyRecords(@PathVariable String hostId) {
        return hostService.getModifyRecords(parseId(hostId));
    }

    /** 保存主机设置（主机名称） */
    @PutMapping("/{hostId}/settings")
    public Map<String, Boolean> updateSettings(@PathVariable String hostId,
                                               @RequestBody Map<String, String> body) {
        hostService.updateSettings(parseId(hostId), body.get("name"));
        return Collections.singletonMap("success", true);
    }

    private static long parseId(String hostId) {
        try {
            return Long.parseLong(hostId);
        } catch (NumberFormatException e) {
            throw new BizException("主机 id 非法: " + hostId);
        }
    }
}
