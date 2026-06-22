package com.dataconnect.service;

import com.dataconnect.entity.DebugLog;
import com.dataconnect.repository.DebugLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DebugLogService {

    private static final Logger log = LoggerFactory.getLogger(DebugLogService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Autowired
    private DebugLogRepository debugLogRepository;

    public void save(Long dsId, String operationType, Object config, Map<String, Object> result) {
        try {
            DebugLog debugLog = new DebugLog();
            debugLog.setDsConfigId(dsId);
            debugLog.setOperationType(operationType);
            debugLog.setConfigSnapshot(config != null ? objectMapper.writeValueAsString(config) : "");
            debugLog.setResultStatus(Boolean.TRUE.equals(result.get("success")) ? "SUCCESS" : "FAILED");
            debugLog.setResultSnapshot(objectMapper.writeValueAsString(result));
            if (result.get("duration") instanceof Number) {
                debugLog.setDuration(((Number) result.get("duration")).longValue());
            }
            debugLogRepository.save(debugLog);
        } catch (Exception e) {
            log.warn("保存调试日志失败, dsId={}, operationType={}", dsId, operationType, e);
        }
    }

    public List<DebugLog> listByDsConfigId(Long dsConfigId) {
        return debugLogRepository.findByDsConfigIdOrderByCreateTimeDesc(dsConfigId);
    }
}
