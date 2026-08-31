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
    private static final String VISUAL_PATH = "./logs/visual/";
    private static final int MAX_TEMPLATE_LOGS = 30;

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

    public Map<String, Object> loadVisualWatermark(Long templateId) {
        Path file = getVisualWatermarkPath(templateId);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String content = new String(Files.readAllBytes(file));
            return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("可视化模板水位线读取失败, templateId={}, error={}", templateId, e.getMessage());
            return null;
        }
    }

    public void saveVisualWatermark(Long templateId, Map<String, Object> watermarkData) {
        Path file = getVisualWatermarkPath(templateId);
        try {
            Files.createDirectories(file.getParent());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(watermarkData);
            Files.write(file, json.getBytes());
            log.info("可视化模板水位线已保存, templateId={}, lastValue={}, lastOffset={}",
                    templateId, watermarkData.get("lastValue"), watermarkData.get("lastOffset"));
        } catch (IOException e) {
            log.error("可视化模板水位线写入失败, templateId={}", templateId, e);
        }
    }

    public boolean deleteVisualWatermark(Long templateId) {
        Path file = getVisualWatermarkPath(templateId);
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("删除可视化模板水位线失败, templateId={}", templateId, e);
            return false;
        }
    }

    /**
     * Write execution log to a timestamped JSON file.
     */
    public String writeExecutionLog(Long flowConfigId, Map<String, Object> executionData) {
        return writeExecutionLogTo(getFlowDir(flowConfigId), flowConfigId, "flow", executionData, 0);
    }

    public String writeTemplateExecutionLog(Long templateId, Map<String, Object> executionData) {
        String filename = writeExecutionLogTo(getVisualDir(templateId), templateId, "visual", executionData, MAX_TEMPLATE_LOGS);
        return filename;
    }

    public List<String> listTemplateExecutionLogs(Long templateId) {
        return listExecutionLogsIn(getVisualDir(templateId));
    }

    public Map<String, Object> readTemplateExecutionLog(Long templateId, String filename) {
        return readExecutionLogIn(getVisualDir(templateId), filename);
    }

    private String writeExecutionLogTo(Path dir, Long id, String kind, Map<String, Object> executionData, int keepMax) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        String filename = "execution-" + timestamp + ".json";
        Path file = dir.resolve(filename);
        try {
            Files.createDirectories(dir);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(executionData);
            Files.write(file, json.getBytes());
            log.info("执行日志已写入, kind={}, id={}, file={}", kind, id, filename);
            if (keepMax > 0) {
                trimOldLogs(dir, keepMax);
            }
        } catch (IOException e) {
            log.error("执行日志写入失败, kind={}, id={}", kind, id, e);
        }
        return filename;
    }

    private void trimOldLogs(Path dir, int keepMax) {
        List<String> files = listExecutionLogsIn(dir);
        if (files.size() <= keepMax) {
            return;
        }
        for (int i = keepMax; i < files.size(); i++) {
            try {
                Files.deleteIfExists(dir.resolve(files.get(i)));
            } catch (IOException ignored) {
            }
        }
    }

    private List<String> listExecutionLogsIn(Path dir) {
        if (!Files.exists(dir)) return Collections.emptyList();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith("execution-") && p.getFileName().toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("列出执行日志失败, dir={}", dir, e);
            return Collections.emptyList();
        }
    }

    private Map<String, Object> readExecutionLogIn(Path dir, String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            log.warn("非法文件名, filename={}", filename);
            return null;
        }
        Path file = dir.resolve(filename);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String content = new String(Files.readAllBytes(file));
            return objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("执行日志读取失败, file={}", file, e);
            return null;
        }
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

    /**
     * Delete a specific execution log file.
     */
    public boolean deleteExecutionLog(Long flowConfigId, String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            log.warn("非法文件名, flowConfigId={}, filename={}", flowConfigId, filename);
            return false;
        }
        Path file = getFlowDir(flowConfigId).resolve(filename);
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("删除执行日志失败, flowConfigId={}, file={}", flowConfigId, filename, e);
            return false;
        }
    }

    /**
     * Delete all execution log files for a flow.
     */
    public int deleteAllExecutionLogs(Long flowConfigId) {
        Path dir = getFlowDir(flowConfigId);
        if (!Files.exists(dir)) return 0;
        int count = 0;
        try {
            List<String> files = listExecutionLogs(flowConfigId);
            for (String fname : files) {
                if (deleteExecutionLog(flowConfigId, fname)) count++;
            }
        } catch (Exception e) {
            log.warn("批量删除执行日志失败, flowConfigId={}", flowConfigId, e);
        }
        return count;
    }

    private Path getFlowDir(Long flowConfigId) {
        return Paths.get(BASE_PATH + flowConfigId);
    }

    private Path getVisualDir(Long templateId) {
        return Paths.get(VISUAL_PATH + templateId);
    }

    private Path getWatermarkPath(Long flowConfigId) {
        return getFlowDir(flowConfigId).resolve("watermark.json");
    }

    private Path getVisualWatermarkPath(Long templateId) {
        return getVisualDir(templateId).resolve("watermark.json");
    }

    private Path getSyncedIdsPath(Long flowConfigId) {
        return getFlowDir(flowConfigId).resolve("synced-ids.json");
    }

    /**
     * Load synced UUID set for SYNCED_SET strategy.
     */
    public Set<String> loadSyncedIds(Long flowConfigId) {
        Path file = getSyncedIdsPath(flowConfigId);
        if (!Files.exists(file)) return new LinkedHashSet<>();
        try {
            String content = new String(Files.readAllBytes(file));
            List<String> list = objectMapper.readValue(content, new TypeReference<List<String>>() {});
            return new LinkedHashSet<>(list);
        } catch (Exception e) {
            log.warn("同步ID文件读取失败, flowConfigId={}", flowConfigId);
            return new LinkedHashSet<>();
        }
    }

    /**
     * Save synced UUID set for SYNCED_SET strategy.
     */
    public void saveSyncedIds(Long flowConfigId, Set<String> ids) {
        Path file = getSyncedIdsPath(flowConfigId);
        try {
            Files.createDirectories(file.getParent());
            List<String> list = new ArrayList<>(ids);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(list);
            Files.write(file, json.getBytes());
            log.info("同步ID已保存, flowConfigId={}, count={}", flowConfigId, ids.size());
        } catch (IOException e) {
            log.error("同步ID文件写入失败, flowConfigId={}", flowConfigId, e);
        }
    }
}
