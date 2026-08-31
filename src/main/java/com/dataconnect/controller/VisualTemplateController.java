package com.dataconnect.controller;

import com.dataconnect.dto.ApiResponse;
import com.dataconnect.entity.VisualTemplate;
import com.dataconnect.service.DataSourceService;
import com.dataconnect.service.MappingTemplateService;
import com.dataconnect.service.VisualTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * 可视化模板控制器
 * 路由: /visual/*
 */
@Controller
@RequestMapping("/visual")
public class VisualTemplateController {

    private static final Logger log = LoggerFactory.getLogger(VisualTemplateController.class);
    private static final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Autowired
    private VisualTemplateService visualTemplateService;

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private MappingTemplateService mappingTemplateService;

    @Autowired
    private com.dataconnect.service.EventDefinitionService eventDefinitionService;

    @Autowired
    private com.dataconnect.service.VisualTemplateExecutionService executionService;

    @Autowired
    private com.dataconnect.service.ExecutionLogFileService executionLogFileService;

    /**
     * 可视化模板列表页
     */
    @GetMapping("/list")
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Long categoryId,
                       Model model) {
        log.info("访问可视化模板列表页, keyword={}, categoryId={}", keyword, categoryId);

        List<VisualTemplate> list;
        if (keyword != null && !keyword.isEmpty()) {
            list = visualTemplateService.search(keyword);
        } else if (categoryId != null && categoryId > 0) {
            list = visualTemplateService.listByCategory(categoryId);
        } else {
            list = visualTemplateService.listAll();
        }

        model.addAttribute("activeMenu", "visual");
        model.addAttribute("pageTitle", "可视化模板");
        model.addAttribute("list", list);
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("dataSources", dataSourceService.listAll());
        model.addAttribute("allTemplates", visualTemplateService.listAll());
        // 解析每个模板的inputType，供前端判断是否显示执行按钮
        java.util.Map<String, String> templateInputTypes = new java.util.LinkedHashMap<>();
        for (VisualTemplate tpl : list) {
            try {
                if (tpl.getEventConfig() != null && !tpl.getEventConfig().isEmpty()) {
                    java.util.Map<String, Object> cfg = objectMapper.readValue(tpl.getEventConfig(),
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
                    java.util.Map<String, Object> input = (java.util.Map<String, Object>) cfg.get("input");
                    if (input != null) {
                        templateInputTypes.put(String.valueOf(tpl.getId()), String.valueOf(input.getOrDefault("inputType", "MANUAL")));
                    }
                }
            } catch (Exception e) { /* ignore */ }
        }
        model.addAttribute("templateInputTypes", templateInputTypes);
        model.addAttribute("mappingTemplates", mappingTemplateService.listAll());
        return "visual/list";
    }

    /**
     * 删除可视化模板
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("删除可视化模板, id={}", id);
        try {
            visualTemplateService.softDelete(id);
            redirectAttributes.addFlashAttribute("success", "删除成功");
        } catch (Exception e) {
            log.error("删除可视化模板失败, id={}", id, e);
            redirectAttributes.addFlashAttribute("error", "删除失败: " + e.getMessage());
        }
        return "redirect:/visual/list";
    }

    // ============ API 接口 ============

    /**
     * 获取模板详情
     */
    @GetMapping("/api/detail/{id}")
    @ResponseBody
    public ApiResponse<VisualTemplate> getDetail(@PathVariable Long id) {
        return visualTemplateService.getById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error("模板不存在"));
    }

    /**
     * 删除模板（API）
     */
    @PostMapping("/api/delete/{id}")
    @ResponseBody
    public ApiResponse<String> deleteApi(@PathVariable Long id) {
        try {
            visualTemplateService.softDelete(id);
            return ApiResponse.success("删除成功");
        } catch (Exception e) {
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 保存模板（API）
     */
    @PostMapping("/api/save")
    @ResponseBody
    public ApiResponse<VisualTemplate> saveApi(@RequestBody VisualTemplate template) {
        try {
            log.info("保存可视化模板(API), id={}, name={}", template.getId(), template.getName());
            VisualTemplate saved;
            if (template.getId() != null) {
                saved = visualTemplateService.update(template.getId(), template);
            } else {
                saved = visualTemplateService.save(template);
            }
            return ApiResponse.success(saved);
        } catch (Exception e) {
            log.error("保存可视化模板失败", e);
            return ApiResponse.error("保存失败: " + e.getMessage());
        }
    }

    /**
     * 测试执行模板（API）
     */
    @PostMapping("/api/execute/{id}")
    @ResponseBody
    public Object executeApi(@PathVariable Long id,
                            @RequestBody(required = false) Map<String, Object> inputData,
                            javax.servlet.http.HttpServletResponse response) {
        try {
            log.info("测试执行模板, id={}", id);
            com.dataconnect.component.DataPacket input;
            if (inputData != null && !inputData.isEmpty()) {
                input = com.dataconnect.component.DataPacket.of(inputData);
            } else {
                input = com.dataconnect.component.DataPacket.empty();
            }
            com.dataconnect.component.DataPacket result = executionService.execute(id, input);

            // 检测文件下载 → 返回JSON，前端用blob下载
            if (result.getVariables() != null && Boolean.TRUE.equals(result.getVariables().get("_download"))) {
                String content = (String) result.getVariables().get("_download_content");
                String contentType = (String) result.getVariables().getOrDefault("_download_contentType", "application/octet-stream");
                String fileName = (String) result.getVariables().getOrDefault("_download_fileName", "export.txt");
                Map<String, Object> resp = new java.util.LinkedHashMap<>();
                resp.put("success", true);
                resp.put("rowCount", 0);
                resp.put("_download", true);
                resp.put("_download_content", content);
                resp.put("_download_contentType", contentType);
                resp.put("_download_fileName", fileName);
                copyExecLog(result, resp);
                return ApiResponse.success(resp);
            }

            Map<String, Object> resp = new java.util.LinkedHashMap<>();
            resp.put("success", result.isSuccess());
            resp.put("rows", result.getRows());
            resp.put("rowCount", result.size());
            resp.put("variables", result.getVariables());
            if (!result.isSuccess()) {
                resp.put("errorCode", result.getErrorCode());
                resp.put("errorMessage", result.getErrorMessage());
            }
            copyExecLog(result, resp);
            return ApiResponse.success(resp);
        } catch (Exception e) {
            log.error("测试执行模板失败, id={}", id, e);
            return ApiResponse.error("执行失败: " + e.getMessage());
        }
    }

    @PostMapping("/api/execute-async/{id}")
    @ResponseBody
    public Object executeAsync(@PathVariable Long id,
                               @RequestBody(required = false) Map<String, Object> inputData) {
        try {
            com.dataconnect.component.DataPacket input;
            if (inputData != null && !inputData.isEmpty()) {
                input = com.dataconnect.component.DataPacket.of(inputData);
            } else {
                input = com.dataconnect.component.DataPacket.empty();
            }
            String runId = executionService.startAsync(id, input);
            Map<String, Object> resp = new java.util.LinkedHashMap<>();
            resp.put("runId", runId);
            return ApiResponse.success(resp);
        } catch (Exception e) {
            log.error("启动模板执行失败, id={}", id, e);
            return ApiResponse.error("启动失败: " + e.getMessage());
        }
    }

    @GetMapping("/api/execute-status/{runId}")
    @ResponseBody
    public Object executeStatus(@PathVariable String runId) {
        Map<String, Object> snap = executionService.getLiveRun(runId);
        if (snap == null) {
            return ApiResponse.error("运行记录不存在");
        }
        return ApiResponse.success(snap);
    }

    private void copyExecLog(com.dataconnect.component.DataPacket result, Map<String, Object> resp) {
        if (result.getVariables() == null) {
            return;
        }
        if (result.getVariables().get("_executionLog") != null) {
            resp.put("executionLog", result.getVariables().get("_executionLog"));
        }
        if (result.getVariables().get("_executionLogFile") != null) {
            resp.put("executionLogFile", result.getVariables().get("_executionLogFile"));
        }
    }

    @GetMapping("/api/exec-logs/{id}")
    @ResponseBody
    public Object listExecLogs(@PathVariable Long id) {
        List<String> files = executionLogFileService.listTemplateExecutionLogs(id);
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (String file : files) {
            Map<String, Object> rec = executionLogFileService.readTemplateExecutionLog(id, file);
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("file", file);
            if (rec != null) {
                item.put("success", rec.get("success"));
                item.put("startTime", rec.get("startTime"));
                item.put("endTime", rec.get("endTime"));
                item.put("durationMs", rec.get("durationMs"));
                item.put("rowCount", rec.get("rowCount"));
                item.put("errorMessage", rec.get("errorMessage"));
            }
            items.add(item);
        }
        return ApiResponse.success(items);
    }

    @GetMapping("/api/exec-logs/{id}/{filename:.+}")
    @ResponseBody
    public Object readExecLog(@PathVariable Long id, @PathVariable String filename) {
        Map<String, Object> rec = executionLogFileService.readTemplateExecutionLog(id, filename);
        if (rec == null) {
            return ApiResponse.error("日志不存在");
        }
        return ApiResponse.success(rec);
    }

    @GetMapping("/api/watermark/{id}")
    @ResponseBody
    public ApiResponse<Map<String, Object>> getWatermark(@PathVariable Long id) {
        Map<String, Object> wm = executionLogFileService.loadVisualWatermark(id);
        if (wm == null) {
            wm = new java.util.LinkedHashMap<>();
            wm.put("exists", false);
        } else {
            wm.put("exists", true);
        }
        return ApiResponse.success(wm);
    }

    @PostMapping("/api/watermark/{id}/reset")
    @ResponseBody
    public ApiResponse<String> resetWatermark(@PathVariable Long id) {
        executionLogFileService.deleteVisualWatermark(id);
        return ApiResponse.success("水位线已重置，下次将按全量/从头开始");
    }
}
