package com.dataconnect.service;

import com.dataconnect.component.DataPacket;
import com.dataconnect.component.ExecutionContext;
import com.dataconnect.entity.VisualTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模板执行过程中的内存日志，供页面轮询展示。
 */
@Component
public class TemplateRunRegistry {

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final long TTL_MS = 30 * 60 * 1000L;

    public static class LiveRun {
        private final String id;
        private final Long templateId;
        private final String templateName;
        private volatile String status = "RUNNING";
        private volatile Boolean success;
        private volatile Integer rowCount;
        private volatile String errorMessage;
        private volatile Map<String, Object> download;
        private final List<Map<String, Object>> logs = new ArrayList<>();
        private final long createdAt = System.currentTimeMillis();

        LiveRun(String id, Long templateId, String templateName) {
            this.id = id;
            this.templateId = templateId;
            this.templateName = templateName;
        }

        public synchronized Map<String, Object> snapshot() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("runId", id);
            m.put("templateId", templateId);
            m.put("templateName", templateName);
            m.put("status", status);
            m.put("success", success);
            m.put("rowCount", rowCount);
            m.put("errorMessage", errorMessage);
            m.put("download", download);
            m.put("logs", new ArrayList<>(logs));
            return m;
        }
    }

    private final ConcurrentHashMap<String, LiveRun> runs = new ConcurrentHashMap<>();

    public LiveRun start(String runId, VisualTemplate template) {
        purgeExpired();
        return runs.computeIfAbsent(runId, k -> new LiveRun(runId, template.getId(), template.getName()));
    }

    public void append(String runId, ExecutionContext.ExecutionLog item) {
        LiveRun run = runs.get(runId);
        if (run == null || item == null) {
            return;
        }
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("time", formatTime(item.getTimestamp()));
        line.put("level", item.getLevel() != null ? item.getLevel().name() : "INFO");
        line.put("message", item.getMessage());
        synchronized (run) {
            run.logs.add(line);
        }
    }

    public void finish(String runId, DataPacket result) {
        LiveRun run = runs.get(runId);
        if (run == null) {
            return;
        }
        boolean ok = result != null && result.isSuccess();
        run.success = ok;
        run.status = ok ? "SUCCESS" : "FAILED";
        run.rowCount = result != null ? result.size() : 0;
        run.errorMessage = result != null ? result.getErrorMessage() : null;
        if (result != null && result.getVariables() != null
                && Boolean.TRUE.equals(result.getVariables().get("_download"))) {
            Map<String, Object> dl = new LinkedHashMap<>();
            dl.put("content", result.getVariables().get("_download_content"));
            dl.put("contentType", result.getVariables().get("_download_contentType"));
            dl.put("fileName", result.getVariables().get("_download_fileName"));
            run.download = dl;
        }
    }

    public void fail(String runId, String message) {
        LiveRun run = runs.get(runId);
        if (run == null) {
            return;
        }
        run.success = false;
        run.status = "FAILED";
        run.errorMessage = message;
    }

    public Map<String, Object> snapshot(String runId) {
        LiveRun run = runs.get(runId);
        return run != null ? run.snapshot() : null;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        runs.entrySet().removeIf(e -> now - e.getValue().createdAt > TTL_MS);
    }

    private static String formatTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault()).format(LOG_TIME);
    }
}
