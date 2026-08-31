package com.dataconnect.service;

import com.dataconnect.entity.TaskConfig;
import com.dataconnect.entity.VisualTemplate;
import com.dataconnect.repository.TaskConfigRepository;
import com.dataconnect.repository.VisualTemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 可视化模板定时：把输入事件里的 CRON 同步到任务管理，由 TaskScheduleService 统一调度。
 */
@Service
public class VisualTemplateScheduleService {

    private static final Logger log = LoggerFactory.getLogger(VisualTemplateScheduleService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private VisualTemplateRepository visualTemplateRepository;

    @Autowired
    private TaskConfigRepository taskConfigRepository;

    @Autowired
    @Lazy
    private TaskScheduleService taskScheduleService;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void restoreOnStartup() {
        List<VisualTemplate> list = visualTemplateRepository.findByIsDeletedOrderByUpdateTimeDesc(0);
        int n = 0;
        for (VisualTemplate t : list) {
            if (sync(t, false)) {
                n++;
            }
        }
        log.info("可视化模板定时已同步到任务管理, {} 个", n);
    }

    /**
     * 按当前模板配置创建/更新或移除对应任务。返回是否仍有定时任务。
     */
    public boolean sync(VisualTemplate template) {
        return sync(template, true);
    }

    public boolean sync(VisualTemplate template, boolean startIfRunning) {
        if (template == null || template.getId() == null) {
            return false;
        }
        CronSpec spec = parseCron(template);
        boolean hasCron = spec != null
                && "CRON".equalsIgnoreCase(spec.inputType)
                && spec.cronExpr != null
                && !spec.cronExpr.isEmpty();
        if (!hasCron) {
            removeTask(template.getId());
            return false;
        }
        TaskConfig task = taskConfigRepository.findFirstByVisualTemplateIdAndTaskType(template.getId(), "VISUAL")
                .orElseGet(TaskConfig::new);
        boolean isNew = task.getId() == null;
        task.setName(template.getName());
        task.setTaskType("VISUAL");
        task.setVisualTemplateId(template.getId());
        task.setFlowConfigId(0L);
        task.setCronExpr(normalizeCron(spec.cronExpr));
        if (isNew) {
            task.setStatus("RUNNING");
        }
        TaskConfig saved = taskConfigRepository.save(task);
        log.info("可视化模板定时已同步到任务, templateId={}, taskId={}, cron={}, status={}",
                template.getId(), saved.getId(), saved.getCronExpr(), saved.getStatus());
        if (startIfRunning && "RUNNING".equals(saved.getStatus())) {
            try {
                taskScheduleService.startTask(saved.getId());
            } catch (Exception e) {
                log.error("启动可视化模板定时失败, templateId={}, taskId={}", template.getId(), saved.getId(), e);
                return false;
            }
        }
        return true;
    }

    public void stop(Long templateId) {
        removeTask(templateId);
    }

    /**
     * 任务管理里删除可视化任务时，把模板输入改回手动，避免下次保存/启动又建回来。
     */
    public void disableCronOnTemplate(Long templateId) {
        if (templateId == null) {
            return;
        }
        VisualTemplate template = visualTemplateRepository.findById(templateId).orElse(null);
        if (template == null || template.getEventConfig() == null || template.getEventConfig().isEmpty()) {
            return;
        }
        try {
            Map<String, Object> cfg = objectMapper.readValue(template.getEventConfig(),
                    new TypeReference<Map<String, Object>>() {});
            Object inputObj = cfg.get("input");
            if (!(inputObj instanceof Map)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> input = (Map<String, Object>) inputObj;
            input.put("inputType", "MANUAL");
            template.setEventConfig(objectMapper.writeValueAsString(cfg));
            visualTemplateRepository.save(template);
            log.info("已将可视化模板输入改为手动触发, id={}", templateId);
        } catch (Exception e) {
            log.warn("关闭可视化模板定时配置失败, id={}", templateId, e);
        }
    }

    /**
     * 任务表单改 Cron 时写回模板输入事件，两边保持一致。
     */
    public void updateCronExpr(Long templateId, String cronExpr) {
        if (templateId == null || cronExpr == null) {
            return;
        }
        VisualTemplate template = visualTemplateRepository.findById(templateId).orElse(null);
        if (template == null || template.getEventConfig() == null || template.getEventConfig().isEmpty()) {
            return;
        }
        try {
            Map<String, Object> cfg = objectMapper.readValue(template.getEventConfig(),
                    new TypeReference<Map<String, Object>>() {});
            Object inputObj = cfg.get("input");
            if (!(inputObj instanceof Map)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> input = (Map<String, Object>) inputObj;
            input.put("inputType", "CRON");
            input.put("cronExpr", cronExpr.trim());
            template.setEventConfig(objectMapper.writeValueAsString(cfg));
            visualTemplateRepository.save(template);
        } catch (Exception e) {
            log.warn("回写可视化模板 Cron 失败, id={}", templateId, e);
        }
    }

    private void removeTask(Long templateId) {
        if (templateId == null || templateId <= 0) {
            return;
        }
        taskConfigRepository.findFirstByVisualTemplateIdAndTaskType(templateId, "VISUAL").ifPresent(task -> {
            log.info("移除可视化模板对应任务, templateId={}, taskId={}", templateId, task.getId());
            taskScheduleService.delete(task.getId());
        });
    }

    @SuppressWarnings("unchecked")
    private CronSpec parseCron(VisualTemplate template) {
        if (template.getEventConfig() == null || template.getEventConfig().isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> cfg = objectMapper.readValue(template.getEventConfig(),
                    new TypeReference<Map<String, Object>>() {});
            Object inputObj = cfg.get("input");
            if (!(inputObj instanceof Map)) {
                return null;
            }
            Map<String, Object> input = (Map<String, Object>) inputObj;
            CronSpec spec = new CronSpec();
            spec.inputType = input.get("inputType") != null ? String.valueOf(input.get("inputType")) : "MANUAL";
            spec.cronExpr = input.get("cronExpr") != null ? String.valueOf(input.get("cronExpr")).trim() : "";
            return spec;
        } catch (Exception e) {
            log.warn("解析可视化模板定时配置失败, id={}", template.getId());
            return null;
        }
    }

    static String normalizeCron(String expr) {
        if (expr == null) {
            return "";
        }
        String t = expr.trim();
        String[] parts = t.split("\\s+");
        if (parts.length == 5) {
            return "0 " + t;
        }
        return t;
    }

    private static class CronSpec {
        String inputType;
        String cronExpr;
    }
}
