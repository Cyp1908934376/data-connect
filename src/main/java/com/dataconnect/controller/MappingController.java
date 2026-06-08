package com.dataconnect.controller;

import com.dataconnect.dto.ApiResponse;
import com.dataconnect.entity.ColumnConfig;
import com.dataconnect.entity.MappingTemplate;
import com.dataconnect.service.ColumnConfigService;
import com.dataconnect.service.DataSourceService;
import com.dataconnect.service.MappingTemplateService;
import com.dataconnect.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/mapping")
public class MappingController {

    private static final Logger log = LoggerFactory.getLogger(MappingController.class);

    @Autowired
    private ColumnConfigService columnConfigService;

    @Autowired
    private MappingTemplateService mappingTemplateService;

    @Autowired
    private TemplateService templateService;

    @Autowired
    private DataSourceService dataSourceService;

    // === 页面路由 ===

    @GetMapping("/columnConfig")
    public String columnConfigList(@RequestParam(required = false) String keyword, Model model) {
        log.info("访问列配置列表页, keyword={}", keyword);
        List<ColumnConfig> configs;
        if (keyword != null && !keyword.isEmpty()) {
            configs = columnConfigService.search(keyword);
        } else {
            configs = columnConfigService.listAll();
        }
        model.addAttribute("activeMenu", "mapping");
        model.addAttribute("pageTitle", "列配置管理");
        model.addAttribute("configs", configs);
        model.addAttribute("templates", templateService.listAll());
        return "mapping/columnConfig";
    }

    @GetMapping("/templateList")
    public String templateList(@RequestParam(required = false) String keyword, Model model) {
        log.info("访问对接模板列表页, keyword={}", keyword);
        List<MappingTemplate> templates;
        if (keyword != null && !keyword.isEmpty()) {
            templates = mappingTemplateService.search(keyword);
        } else {
            templates = mappingTemplateService.listAll();
        }
        model.addAttribute("activeMenu", "mapping");
        model.addAttribute("pageTitle", "对接模板");
        model.addAttribute("templates", templates);
        // Resolve datasource names for display (use String keys for FreeMarker compatibility)
        Map<String, String> dsMap = new LinkedHashMap<>();
        dataSourceService.getIdNameMap().forEach((k, v) -> dsMap.put(String.valueOf(k), v));
        model.addAttribute("dsConfigMap", dsMap);
        // Resolve column config names
        model.addAttribute("columnConfigMap", columnConfigService.listAll());
        return "mapping/templateList";
    }

    @GetMapping("/templateForm")
    public String templateForm(@RequestParam(required = false) Long id, Model model) {
        log.info("访问对接模板表单页, id={}", id);
        MappingTemplate template = id != null ?
                mappingTemplateService.getById(id).orElse(new MappingTemplate()) : new MappingTemplate();
        model.addAttribute("activeMenu", "mapping");
        model.addAttribute("pageTitle", id != null ? "编辑对接模板" : "新增对接模板");
        model.addAttribute("template", template);
        model.addAttribute("dataSources", dataSourceService.listAll());
        model.addAttribute("columnConfigs", columnConfigService.listByType("RECEIVE"));
        model.addAttribute("templates", templateService.listAll());
        return "mapping/templateForm";
    }

    // === REST API ===

    @PostMapping("/api/saveColumnConfig")
    @ResponseBody
    public ApiResponse<ColumnConfig> saveColumnConfig(ColumnConfig config) {
        log.info("保存列配置, id={}, name={}", config.getId(), config.getName());
        try {
            if (config.getId() != null) {
                return ApiResponse.success(columnConfigService.update(config.getId(), config));
            } else {
                return ApiResponse.success(columnConfigService.save(config));
            }
        } catch (Exception e) {
            log.error("保存列配置失败, id={}", config.getId(), e);
            throw e;
        }
    }

    @PostMapping("/api/deleteColumnConfig/{id}")
    @ResponseBody
    public ApiResponse<Void> deleteColumnConfig(@PathVariable Long id) {
        log.info("删除列配置, id={}", id);
        columnConfigService.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/api/saveTemplate")
    @ResponseBody
    public ApiResponse<MappingTemplate> saveTemplate(MappingTemplate template) {
        log.info("保存对接模板, id={}, name={}", template.getId(), template.getName());
        try {
            if (template.getId() != null) {
                return ApiResponse.success(mappingTemplateService.update(template.getId(), template));
            } else {
                return ApiResponse.success(mappingTemplateService.save(template));
            }
        } catch (Exception e) {
            log.error("保存对接模板失败, id={}", template.getId(), e);
            throw e;
        }
    }

    @PostMapping("/api/deleteTemplate/{id}")
    @ResponseBody
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        log.info("删除对接模板, id={}", id);
        mappingTemplateService.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/api/parsePostman")
    @ResponseBody
    public ApiResponse<List<Map<String, String>>> parsePostman(@RequestParam String postmanJson) {
        log.info("解析Postman JSON, length={}", postmanJson != null ? postmanJson.length() : 0);
        return ApiResponse.success(mappingTemplateService.parsePostmanJson(postmanJson));
    }
}
