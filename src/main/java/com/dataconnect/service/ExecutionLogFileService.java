package com.dataconnect.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ExecutionLogFileService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionLogFileService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String BASE_PATH = "./logs/flow/";

    /**
     * Load watermark from file. Returns null if file does not exist or is corrupted.
     */
    public Map<String, Object> loadWatermark(Long flowConfigId) {
        Path file = getWatermarkPath(flowConfigId);
        if (!Files.exists(file)) {
            log.debug("水位线文件不存在, flowConfigId={}", flowConfigId);
            return null;
        }
        try {
            String content = new String(Files.readAllBytes(file));
            return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("水位线文件读取失败, flowConfigId={}, error={}", flowConfigId, e.getMessage());
            return null;
        }
    }

    /**
     * Save watermark to file, creating parent directories as needed.
     */
    public void saveWatermark(Long flowConfigId, Map<String, Object> watermarkData) {
        Path file = getWatermarkPath(flowConfigId);
        try {
            Files.createDirectories(file.getParent());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(watermarkData);
            Files.write(file, json.getBytes());
            log.info("水位线已保存, flowConfigId={}, lastValue={}", flowConfigId, watermarkData.get("lastValue"));
        } catch (IOException e) {
            log.error("水位线文件写入失败, flowConfigId={}", flowConfigId, e);
        }
    }

    /**
     * Delete watermark for a flow (used when resetting).
     */
    public void deleteWatermark(Long flowConfigId) {
        try {
            Files.deleteIfExists(getWatermarkPath(flowConfigId));
            log.info("水位线已删除, flowConfigId={}", flowConfigId);
        } catch (IOException e) {
            log.warn("删除水位线文件失败, flowConfigId={}", flowConfigId, e);
        }
    }

    /**
     * Write execution log to a timestamped JSON file.
     */
    public String writeExecutionLog(Long flowConfigId, Map<String, Object> executionData) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        String filename = "execution-" + timestamp + ".json";
        Path file = getFlowDir(flowConfigId).resolve(filename);
        try {
            Files.createDirectories(file.getParent());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(executionData);
            Files.write(file, json.getBytes());
            log.info("执行日志已写入, flowConfigId={}, file={}", flowConfigId, filename);
        } catch (IOException e) {
            log.error("执行日志写入失败, flowConfigId={}", flowConfigId, e);
        }
        return filename;
    }

    /**
     * List execution log files for a flow, sorted by name descending (newest first).
     */
    public List<String> listExecutionLogs(Long flowConfigId) {
        Path dir = getFlowDir(flowConfigId);
        if (!Files.exists(dir)) return Collections.emptyList();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith("execution-") && p.getFileName().toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("列出执行日志失败, flowConfigId={}", flowConfigId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Read a specific execution log file.
     */
    public Map<String, Object> readExecutionLog(Long flowConfigId, String filename) {
        // Sanitize filename to prevent path traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            log.warn("非法文件名, flowConfigId={}, filename={}", flowConfigId, filename);
            return null;
        }
        Path file = getFlowDir(flowConfigId).resolve(filename);
        if (!Files.exists(file)) {
            log.warn("执行日志文件不存在, flowConfigId={}, file={}", flowConfigId, filename);
            return null;
        }
        try {
            String content = new String(Files.readAllBytes(file));
            return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("执行日志读取失败, flowConfigId={}, file={}", flowConfigId, filename, e);
            return null;
        }
    }

    private Path getFlowDir(Long flowConfigId) {
        return Paths.get(BASE_PATH + flowConfigId);
    }

    private Path getWatermarkPath(Long flowConfigId) {
        return getFlowDir(flowConfigId).resolve("watermark.json");
    }
}
