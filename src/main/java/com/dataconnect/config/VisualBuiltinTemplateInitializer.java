package com.dataconnect.config;

import com.dataconnect.entity.VisualTemplate;
import com.dataconnect.repository.VisualTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 启动时确保两份系统可视化模板存在：附件下载、论文归档推送。
 * 已存在则不覆盖用户改过的 eventConfig（如下载 URL），仅必要时补齐 callTemplateId。
 */
@Component
public class VisualBuiltinTemplateInitializer {

    private static final Logger log = LoggerFactory.getLogger(VisualBuiltinTemplateInitializer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static final String CODE_FILE_DOWNLOAD = "FILE_DOWNLOAD";
    public static final String CODE_THESIS_ARCHIVE = "THESIS_ARCHIVE";
    public static final long CATEGORY_SYSTEM = 5L;

    @Autowired
    private VisualTemplateRepository visualTemplateRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            visualTemplateRepository.count();
        } catch (Exception e) {
            log.warn("无法查询可视化模板表，跳过系统模板初始化: {}", e.getMessage());
            return;
        }

        try {
            VisualTemplate download = ensureFileDownload();
            ensureThesisArchive(download.getId());
            removeTokenApiMysql();
            log.info("系统可视化模板已就绪: 附件下载 id={}, 论文归档推送", download.getId());
        } catch (Exception e) {
            log.error("初始化系统可视化模板失败", e);
        }
    }

    private VisualTemplate ensureFileDownload() throws Exception {
        Optional<VisualTemplate> existing = visualTemplateRepository.findFirstByBuiltinCode(CODE_FILE_DOWNLOAD);
        if (existing.isPresent()) {
            VisualTemplate t = existing.get();
            restoreIfDeleted(t);
            if (t.getEventConfig() == null || t.getEventConfig().trim().isEmpty()) {
                t.setEventConfig(buildDownloadEventConfig());
                visualTemplateRepository.save(t);
            }
            return t;
        }

        VisualTemplate t = newTemplate(
                "附件下载",
                "系统模板：按 URL 模板下载附件，HTML 自动转 PDF。论文归档推送会调用本模板。",
                "PROCESS",
                CODE_FILE_DOWNLOAD,
                buildDownloadEventConfig(),
                "[]");
        VisualTemplate saved = visualTemplateRepository.save(t);
        log.info("已创建系统模板「附件下载」, id={}", saved.getId());
        return saved;
    }

    private void ensureThesisArchive(Long downloadId) throws Exception {
        Optional<VisualTemplate> existing = visualTemplateRepository.findFirstByBuiltinCode(CODE_THESIS_ARCHIVE);
        if (existing.isPresent()) {
            VisualTemplate t = existing.get();
            restoreIfDeleted(t);
            if (t.getEventConfig() == null || t.getEventConfig().trim().isEmpty()) {
                t.setEventConfig(buildThesisEventConfig(downloadId));
                t.setInputParams(buildThesisInputParams());
                visualTemplateRepository.save(t);
            } else {
                syncCallTemplateId(t, downloadId);
            }
            return;
        }

        VisualTemplate t = newTemplate(
                "论文归档推送",
                "系统模板：先调用「附件下载」，再 ZIP 打包并推送到档案 file2Archives。业务模板请在输出事件中选择本模板。",
                "OUTPUT",
                CODE_THESIS_ARCHIVE,
                buildThesisEventConfig(downloadId),
                buildThesisInputParams());
        VisualTemplate saved = visualTemplateRepository.save(t);
        log.info("已创建系统模板「论文归档推送」, id={}", saved.getId());
    }

    /** 该内置模板已废弃，启动时清掉已落地的记录。 */
    private void removeTokenApiMysql() {
        Optional<VisualTemplate> existing = visualTemplateRepository.findFirstByBuiltinCode("TOKEN_API_MYSQL");
        if (!existing.isPresent()) {
            return;
        }
        VisualTemplate t = existing.get();
        visualTemplateRepository.delete(t);
        log.info("已移除废弃系统模板「Token接口同步MySQL」, id={}", t.getId());
    }

    private VisualTemplate newTemplate(String name, String description, String eventType,
            String builtinCode, String eventConfig, String inputParams) {
        VisualTemplate t = new VisualTemplate();
        t.setName(name);
        t.setDescription(description);
        t.setEventType(eventType);
        t.setCategoryId(CATEGORY_SYSTEM);
        t.setBuiltinCode(builtinCode);
        t.setEventConfig(eventConfig);
        t.setInputParams(inputParams);
        t.setOutputParams("[]");
        t.setCanvasConfig("{}");
        t.setCallTemplates("[]");
        t.setIsDeleted(0);
        t.setVersion(1);
        return t;
    }

    private void restoreIfDeleted(VisualTemplate t) {
        if (t.getIsDeleted() != null && t.getIsDeleted() == 1) {
            t.setIsDeleted(0);
            visualTemplateRepository.save(t);
            log.info("已恢复被删除的系统模板: {}", t.getBuiltinCode());
        }
    }

    @SuppressWarnings("unchecked")
    private void syncCallTemplateId(VisualTemplate thesis, Long downloadId) {
        try {
            Map<String, Object> cfg = objectMapper.readValue(thesis.getEventConfig(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            Object stepsObj = cfg.get("steps");
            if (!(stepsObj instanceof List)) {
                return;
            }
            boolean changed = false;
            for (Object item : (List<?>) stepsObj) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<String, Object> step = (Map<String, Object>) item;
                if (!"CALL_TEMPLATE".equals(String.valueOf(step.get("type")))) {
                    continue;
                }
                Long curId = toLong(step.get("callTemplateId"));
                if (curId == null || curId == 0L || !isUsableTemplate(curId)) {
                    step.put("callTemplateId", downloadId);
                    changed = true;
                }
            }
            if (changed) {
                thesis.setEventConfig(objectMapper.writeValueAsString(cfg));
                visualTemplateRepository.save(thesis);
                log.info("已校正论文归档推送的附件下载模板 ID: {}", downloadId);
            }
        } catch (Exception e) {
            log.warn("校正论文归档推送 callTemplateId 失败: {}", e.getMessage());
        }
    }

    private boolean isUsableTemplate(Long id) {
        return visualTemplateRepository.findById(id)
                .filter(t -> t.getIsDeleted() == null || t.getIsDeleted() == 0)
                .isPresent();
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String buildDownloadEventConfig() throws Exception {
        Map<String, Object> config = new LinkedHashMap<>();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("inputType", "MANUAL");
        config.put("input", input);

        Map<String, Object> download = new LinkedHashMap<>();
        download.put("type", "FILE_DOWNLOAD");
        download.put("urlTemplate", "http://202.115.194.60/Interface/Dag_Sr.aspx?xh=${学号}");
        download.put("fileNameTemplate", "${学号}_成绩表.pdf");
        download.put("convertMode", "AUTO");
        download.put("headers", "");
        download.put("fontPath", "");
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(download);
        config.put("steps", steps);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("outputMode", "RETURN");
        config.put("output", output);
        return objectMapper.writeValueAsString(config);
    }

    private String buildThesisEventConfig(Long downloadId) throws Exception {
        Map<String, Object> config = new LinkedHashMap<>();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("inputType", "MANUAL");
        config.put("input", input);

        Map<String, Object> call = new LinkedHashMap<>();
        call.put("type", "CALL_TEMPLATE");
        call.put("callTemplateId", downloadId);
        call.put("callParams", new LinkedHashMap<String, Object>());

        Map<String, Object> archive = new LinkedHashMap<>();
        archive.put("type", "THESIS_ARCHIVE");
        archive.put("apiUrl", "${apiUrl}");
        archive.put("appkey", "${appkey}");
        archive.put("password", "${password}");
        archive.put("ccode", "${ccode}");

        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(call);
        steps.add(archive);
        config.put("steps", steps);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("outputMode", "RETURN");
        config.put("output", output);
        return objectMapper.writeValueAsString(config);
    }

    private String buildThesisInputParams() throws Exception {
        List<Map<String, Object>> params = new ArrayList<>();
        params.add(param("apiUrl", true, ""));
        params.add(param("appkey", true, ""));
        params.add(param("password", true, ""));
        params.add(param("ccode", false, "lwdj"));
        return objectMapper.writeValueAsString(params);
    }

    private Map<String, Object> param(String name, boolean required, String defaultValue) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", name);
        p.put("type", "string");
        p.put("required", required);
        p.put("defaultValue", defaultValue);
        return p;
    }
}
