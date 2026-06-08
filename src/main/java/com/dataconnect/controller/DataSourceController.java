package com.dataconnect.controller;

import com.dataconnect.dto.ApiResponse;
import com.dataconnect.entity.DsConfig;
import com.dataconnect.service.ApiClientService;
import com.dataconnect.service.DataSourceService;
import com.dataconnect.service.DebugLogService;
import com.dataconnect.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;

@Controller
@RequestMapping("/datasource")
public class DataSourceController {

    private static final Logger log = LoggerFactory.getLogger(DataSourceController.class);

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private ApiClientService apiClientService;

    @Autowired
    private DebugLogService debugLogService;

    @Autowired
    private TemplateService templateService;

    @GetMapping("/list")
    public String list(Model model) {
        log.info("访问数据源列表页");
        model.addAttribute("activeMenu", "datasource");
        model.addAttribute("pageTitle", "数据源管理");
        model.addAttribute("list", dataSourceService.listAll());
        return "datasource/list";
    }

    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id, Model model) {
        log.info("访问数据源表单页, id={}", id);
        DsConfig config = id != null ? dataSourceService.getById(id).orElse(new DsConfig()) : new DsConfig();
        model.addAttribute("activeMenu", "datasource");
        model.addAttribute("pageTitle", id != null ? "编辑数据源" : "新增数据源");
        model.addAttribute("config", config);
        model.addAttribute("templates", templateService.listAll());
        return "datasource/form";
    }

    @PostMapping("/save")
    public String save(DsConfig config, RedirectAttributes redirectAttributes) {
        if (config.getName() == null || config.getName().trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "数据源名称不能为空");
            return config.getId() != null ?
                    "redirect:/datasource/form?id=" + config.getId() :
                    "redirect:/datasource/form";
        }
        log.info("保存数据源, id={}, name={}, type={}", config.getId(), config.getName(), config.getSourceType());
        try {
            if (config.getId() != null) {
                dataSourceService.update(config.getId(), config);
            } else {
                dataSourceService.save(config);
            }
            log.info("数据源保存成功, id={}", config.getId());
            redirectAttributes.addFlashAttribute("success", "保存成功");
            return "redirect:/datasource/list";
        } catch (DataAccessException e) {
            log.error("保存数据源失败 - 数据库错误", e);
            String msg = e.getMostSpecificCause() != null
                    ? e.getMostSpecificCause().getMessage()
                    : e.getMessage();
            redirectAttributes.addFlashAttribute("error", "保存失败: " + msg);
        } catch (RuntimeException e) {
            log.error("保存数据源失败", e);
            redirectAttributes.addFlashAttribute("error", "保存失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("保存数据源失败 - 未知错误", e);
            redirectAttributes.addFlashAttribute("error", "保存失败: " + e.getMessage());
        }
        return config.getId() != null ?
                "redirect:/datasource/form?id=" + config.getId() :
                "redirect:/datasource/form";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("删除数据源, id={}", id);
        try {
            dataSourceService.delete(id);
            log.info("数据源删除成功, id={}", id);
            redirectAttributes.addFlashAttribute("success", "删除成功");
        } catch (Exception e) {
            log.error("删除数据源失败, id={}", id, e);
            redirectAttributes.addFlashAttribute("error", "删除失败: " + e.getMessage());
        }
        return "redirect:/datasource/list";
    }

    @GetMapping("/debug")
    public String debug(@RequestParam(required = false) Long id, Model model) {
        log.info("访问数据源调试页, id={}", id);
        if (id == null) return "redirect:/datasource/list";
        DsConfig config = dataSourceService.getById(id).orElse(null);
        if (config == null) return "redirect:/datasource/list";

        model.addAttribute("activeMenu", "datasource");
        model.addAttribute("pageTitle", "调试: " + config.getName());
        model.addAttribute("config", config);
        model.addAttribute("debugLogs", debugLogService.listByDsConfigId(id));
        return "datasource/debug";
    }

    // === REST API ===
    @GetMapping("/api/testConnection")
    @ResponseBody
    public ApiResponse<Map<String, Object>> testConnection(@RequestParam Long id) {
        log.info("测试数据源连接, id={}", id);
        DsConfig config = dataSourceService.getById(id).orElse(null);
        if (config == null) {
            log.warn("测试连接失败: 数据源不存在, id={}", id);
            return ApiResponse.error("数据源不存在");
        }
        Map<String, Object> result = doTestConnection(config);
        log.info("连接测试结果, id={}, success={}, duration={}ms", id, result.get("success"), result.get("duration"));
        debugLogService.save(config.getId(), "CONNECT_TEST", config, result);
        return ApiResponse.success(result);
    }

    @PostMapping("/api/testConnectionWithConfig")
    @ResponseBody
    public ApiResponse<Map<String, Object>> testConnectionWithConfig(@RequestBody DsConfig config) {
        log.info("测试数据源连接(临时配置), name={}, type={}", config.getName(), config.getSourceType());
        Map<String, Object> result = doTestConnection(config);
        log.info("连接测试结果(临时配置), success={}, duration={}ms", result.get("success"), result.get("duration"));
        return ApiResponse.success(result);
    }

    private Map<String, Object> doTestConnection(DsConfig config) {
        long start = System.currentTimeMillis();
        Map<String, Object> result;
        if ("DB".equals(config.getSourceType())) {
            boolean success = dataSourceService.testConnection(config);
            result = new LinkedHashMap<>();
            result.put("success", success);
            result.put("message", success ? "连接成功" : "连接失败");
        } else {
            result = apiClientService.testConnection(config);
        }
        result.put("duration", System.currentTimeMillis() - start);
        return result;
    }

    @PostMapping("/api/executeApi")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> executeApi(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        log.info("执行API调试, id={}", id);
        DsConfig config = dataSourceService.getById(id).orElse(null);
        if (config == null) {
            log.warn("API调试失败: 数据源不存在, id={}", id);
            return ApiResponse.error("数据源不存在");
        }

        Map<String, String> apiParams = (Map<String, String>) params.get("params");
        if (apiParams == null) apiParams = new HashMap<>();

        long start = System.currentTimeMillis();
        Map<String, Object> result;
        String mode = config.getApiMode() != null ? config.getApiMode() : "SINGLE";
        log.info("API调试模式: {}, id={}", mode, id);
        if ("CHAIN".equals(mode)) {
            result = apiClientService.executeChain(config, apiParams);
        } else if ("SCRIPT".equals(mode)) {
            result = apiClientService.executeWithTemplate(config, apiParams);
        } else {
            result = apiClientService.executeSingleDebug(config, apiParams);
        }
        if (!result.containsKey("duration")) {
            result.put("duration", System.currentTimeMillis() - start);
        }
        log.info("API调试完成, id={}, success={}, duration={}ms", id, result.get("success"), result.get("duration"));
        debugLogService.save(id, "API_TEST", config, result);
        return ApiResponse.success(result);
    }

    @PostMapping("/api/executeQuery")
    @ResponseBody
    public ApiResponse<Map<String, Object>> executeQuery(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        String sql = (String) params.get("sql");
        log.info("执行SQL查询, id={}, sql={}", id, sql != null && sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);
        Map<String, Object> result = dataSourceService.executeQuery(id, sql);
        DsConfig config = dataSourceService.getById(id).orElse(null);
        log.info("SQL查询完成, id={}, success={}, duration={}ms", id, result.get("success"), result.get("duration"));
        debugLogService.save(id, "QUERY_TEST", config, result);
        return ApiResponse.success(result);
    }

    @GetMapping("/api/getTables")
    @ResponseBody
    public ApiResponse<Map<String, Object>> getTables(@RequestParam Long id) {
        log.info("获取表列表, id={}", id);
        Map<String, Object> result = dataSourceService.getTables(id);
        if (Boolean.TRUE.equals(result.get("success"))) {
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> tables = (java.util.List<Map<String, Object>>) result.get("tables");
            log.info("获取表列表成功, id={}, count={}", id, tables != null ? tables.size() : 0);
        } else {
            log.warn("获取表列表失败, id={}, error={}", id, result.get("error"));
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/api/getColumns")
    @ResponseBody
    public ApiResponse<Map<String, Object>> getColumns(@RequestParam Long id, @RequestParam String tableName) {
        log.info("获取表字段, id={}, table={}", id, tableName);
        Map<String, Object> result = dataSourceService.getColumns(id, tableName);
        if (Boolean.TRUE.equals(result.get("success"))) {
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> columns = (java.util.List<Map<String, Object>>) result.get("columns");
            log.info("获取表字段成功, id={}, table={}, count={}", id, tableName, columns != null ? columns.size() : 0);
        } else {
            log.warn("获取表字段失败, id={}, table={}, error={}", id, tableName, result.get("error"));
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/api/previewData")
    @ResponseBody
    public ApiResponse<Map<String, Object>> previewData(@RequestParam Long id,
                                                         @RequestParam String tableName,
                                                         @RequestParam(defaultValue = "20") int limit) {
        log.info("预览数据, id={}, table={}, limit={}", id, tableName, limit);
        Map<String, Object> result = dataSourceService.previewData(id, tableName, limit);
        log.info("预览数据完成, id={}, table={}, success={}", id, tableName, result.get("success"));
        return ApiResponse.success(result);
    }
}
