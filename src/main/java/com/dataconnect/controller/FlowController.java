package com.dataconnect.controller;

import com.dataconnect.dto.ApiResponse;
import com.dataconnect.entity.FlowConfig;
import com.dataconnect.service.DataSourceService;
import com.dataconnect.service.ExecutionLogFileService;
import com.dataconnect.service.FlowConfigService;
import com.dataconnect.service.FlowExecutionService;
import com.dataconnect.service.MappingTemplateService;
import com.dataconnect.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/flow")
public class FlowController {

    private static final Logger log = LoggerFactory.getLogger(FlowController.class);

    @Autowired
    private FlowConfigService flowConfigService;

    @Autowired
    private FlowExecutionService flowExecutionService;

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private TemplateService templateService;

    @Autowired
    private MappingTemplateService mappingTemplateService;

    @Autowired
    private ExecutionLogFileService executionLogFileService;

    @GetMapping("/list")
    public String list(Model model) {
        log.info("访问对接流程列表页");
        model.addAttribute("activeMenu", "flow");
        model.addAttribute("pageTitle", "对接流程");
        model.addAttribute("flows", flowConfigService.listAll());
        return "flow/index";
    }

    @GetMapping("/wizard")
    public String wizard(@RequestParam(required = false) Long id, Model model) {
        log.info("访问流程向导页, flowId={}", id);
        FlowConfig flowConfig = id != null ?
                flowConfigService.getById(id).orElse(new FlowConfig()) : new FlowConfig();
        model.addAttribute("activeMenu", "flow");
        model.addAttribute("pageTitle", "创建/编辑流程");
        model.addAttribute("flowConfig", flowConfig);
        model.addAttribute("dbSources", dataSourceService.listByType("DB"));
        model.addAttribute("apiSources", dataSourceService.listByType("API"));
        // 所有数据源
        model.addAttribute("allSources", dataSourceService.listAll());
        model.addAttribute("templates", templateService.listAll());
        model.addAttribute("mappingTemplates", mappingTemplateService.listAll());
        model.addAttribute("pipelineConfigJson", flowExecutionService.getPipelineConfigJson(flowConfig));
        return "flow/wizard";
    }

    @PostMapping("/save")
    public String save(FlowConfig config) {
        log.info("保存流程配置, id={}, name={}", config.getId(), config.getName());
        try {
            if (config.getId() != null) {
                flowConfigService.update(config.getId(), config);
            } else {
                flowConfigService.save(config);
            }
            log.info("流程配置保存成功, id={}", config.getId());
        } catch (Exception e) {
            log.error("保存流程配置失败, id={}", config.getId(), e);
            throw e;
        }
        return "redirect:/flow/list";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        log.info("删除流程配置, id={}", id);
        try {
            flowConfigService.delete(id);
            log.info("流程配置删除成功, id={}", id);
        } catch (Exception e) {
            log.error("删除流程配置失败, id={}", id, e);
            throw e;
        }
        return "redirect:/flow/list";
    }

    // === REST API ===
    @PostMapping("/api/save")
    @ResponseBody
    public ApiResponse<FlowConfig> apiSave(FlowConfig config) {
        FlowConfig saved;
        if (config.getId() != null) {
            saved = flowConfigService.update(config.getId(), config);
        } else {
            saved = flowConfigService.save(config);
        }
        return ApiResponse.success(saved);
    }

    @PostMapping("/api/execute")
    @ResponseBody
    public ApiResponse<Map<String, Object>> execute(@RequestParam Long flowConfigId) {
        log.info("执行对接流程, flowConfigId={}", flowConfigId);
        Map<String, Object> result = flowExecutionService.execute(flowConfigId);
        if (Boolean.TRUE.equals(result.get("success"))) {
            log.info("对接流程执行成功, flowConfigId={}, duration={}", flowConfigId, result.get("duration"));
            return ApiResponse.success(result);
        }
        log.error("对接流程执行失败, flowConfigId={}, error={}", flowConfigId, result.get("error"));
        return ApiResponse.error((String) result.getOrDefault("error", "执行失败"));
    }

    @GetMapping("/api/logs")
    @ResponseBody
    public ApiResponse<java.util.List<String>> getLogs() {
        return ApiResponse.success(flowExecutionService.getExecutionLogs());
    }

    // === File-based execution log and watermark APIs ===

    @GetMapping("/api/execution-logs/{flowId}")
    @ResponseBody
    public ApiResponse<List<String>> getExecutionLogFiles(@PathVariable Long flowId) {
        return ApiResponse.success(executionLogFileService.listExecutionLogs(flowId));
    }

    @GetMapping("/api/execution-log/{flowId}/{filename}")
    @ResponseBody
    public ApiResponse<Map<String, Object>> getExecutionLogContent(
            @PathVariable Long flowId, @PathVariable String filename) {
        Map<String, Object> log = executionLogFileService.readExecutionLog(flowId, filename);
        return log != null ? ApiResponse.success(log) : ApiResponse.error("日志文件不存在");
    }

    @GetMapping("/api/watermark/{flowId}")
    @ResponseBody
    public ApiResponse<Map<String, Object>> getWatermark(@PathVariable Long flowId) {
        Map<String, Object> wm = executionLogFileService.loadWatermark(flowId);
        return ApiResponse.success(wm);
    }
}
