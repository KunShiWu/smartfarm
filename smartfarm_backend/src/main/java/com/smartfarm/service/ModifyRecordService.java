package com.smartfarm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.smartfarm.config.AppProperties;
import com.smartfarm.model.ModifyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 修改记录服务：本地 JSON 文件持久化（data/modify-records.json）
 */
@Service
public class ModifyRecordService {

    private static final Logger log = LoggerFactory.getLogger(ModifyRecordService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AppProperties props;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final List<ModifyRecord> records = new ArrayList<>();
    private Path storeFile;

    public ModifyRecordService(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        storeFile = Paths.get(props.getDataDir()).resolve("modify-records.json");
        if (Files.exists(storeFile)) {
            try {
                ModifyRecord[] arr = mapper.readValue(storeFile.toFile(), ModifyRecord[].class);
                synchronized (records) {
                    records.addAll(Arrays.asList(arr));
                }
                log.info("加载修改记录 {} 条", arr.length);
            } catch (IOException e) {
                log.warn("修改记录文件读取失败，将从空记录开始: {}", e.getMessage());
            }
        }
    }

    /** 添加一条修改记录（最新在前） */
    public void add(long hostId, String field, String before, String after) {
        ModifyRecord record = new ModifyRecord(
                String.valueOf(hostId),
                LocalDateTime.now().format(FMT),
                field, before, after,
                props.getOperator());
        synchronized (records) {
            records.add(0, record);
            persist();
        }
    }

    /** 查询指定主机的修改记录（最新在前） */
    public List<ModifyRecord> list(long hostId) {
        String id = String.valueOf(hostId);
        synchronized (records) {
            return records.stream()
                    .filter(r -> id.equals(r.hostId))
                    .collect(Collectors.toList());
        }
    }

    private void persist() {
        try {
            Files.createDirectories(storeFile.getParent());
            mapper.writeValue(storeFile.toFile(), records);
        } catch (IOException e) {
            log.error("修改记录持久化失败: {}", e.getMessage());
        }
    }
}
