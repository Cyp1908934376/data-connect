package com.dataconnect.controller;

import com.dataconnect.dto.ApiResponse;
import com.dataconnect.entity.FlowConfig;
import com.dataconnect.entity.PublishConfig;
import com.dataconnect.entity.VisualTemplate;
import com.dataconnect.repository.FlowConfigRepository;
import com.dataconnect.repository.VisualTemplateRepository;
import com.dataconnect.service.PublishConfigService;
import com.dataconnect.service.PublishService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发布管理控制器
 * 路由: /publish/*
 */
@Controller
@RequestMapping("/publish")
public class PublishController {

    private static final Logger log = LoggerFactory.getLogger(PublishController.class);

    @Autowired
    private PublishConfigService publishConfigService;

    @Autowired
    private PublishService publishService;

    @Autowired
    private FlowConfigRepository flowConfigRepository;

    @Autowired
    private VisualTemplateRepository visualTemplateRepository;

    /**
     * 发布列表页
     */
    @GetMapping("/list")
    public String list(Model model) {
        log.info("访问发布列表页");
        List<PublishConfig> list = publishConfigService.listAll();
        
        // 构建流程ID到名称的映射（使用String key兼容FreeMarker）
        Map<String, String> flowMap = new HashMap<>();
        Map<String, String> templateMap = new HashMap<>();
        for (PublishConfig config : list) {
            if (config.getFlowConfigId() != null && config.getFlowConfigId() > 0 && !flowMap.containsKey(config.getFlowConfigId().toString())) {
                flowConfigRepository.findById(config.getFlowConfigId())
                        .ifPresent(fc -> flowMap.put(fc.getId().toString(), fc.getName()));
            }
            if (config.getVisualTemplateId() != null && config.getVisualTemplateId() > 0 && !templateMap.containsKey(config.getVisualTemplateId().toString())) {
                visualTemplateRepository.findById(config.getVisualTemplateId())
                        .ifPresent(vt -> templateMap.put(vt.getId().toString(), vt.getName()));
            }
        }
        
        model.addAttribute("activeMenu", "publish");
        model.addAttribute("pageTitle", "发布管理");
        model.addAttribute("list", list);
        model.addAttribute("flowMap", flowMap);
        model.addAttribute("templateMap", templateMap);
        return "publish/list";
    }

    /**
     * 发布表单页
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id, Model model) {
        log.info("访问发布表单页, id={}", id);
        PublishConfig config = id != null ? 
                publishConfigService.getById(id).orElse(new PublishConfig()) : 
                new PublishConfig();
        
        // 获取所有流程配置
        List<FlowConfig> flowList = flowConfigRepository.findAll();
        List<VisualTemplate> templateList = visualTemplateRepository.findByIsDeletedOrderByUpdateTimeDesc(0);
        
        model.addAttribute("activeMenu", "publish");
        model.addAttribute("pageTitle", id != null ? "编辑发布" : "新增发布");
        model.addAttribute("config", config);
        model.addAttribute("flowList", flowList);
        model.addAttribute("templateList", templateList);
        return "publish/form";
    }

    /**
     * 保存发布配置
     */
    @PostMapping("/save")
    public String save(PublishConfig config, RedirectAttributes redirectAttributes) {
        if (config.getName() == null || config.getName().trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "发布名称不能为空");
            return config.getId() != null ? 
                    "redirect:/publish/form?id=" + config.getId() : 
                    "redirect:/publish/form";
        }
        
        boolean hasFlow = config.getFlowConfigId() != null && config.getFlowConfigId() > 0;
        boolean hasTemplate = config.getVisualTemplateId() != null && config.getVisualTemplateId() > 0;
        if (!hasFlow && !hasTemplate) {
            redirectAttributes.addFlashAttribute("error", "请关联可视化模板");
            return config.getId() != null ? 
                    "redirect:/publish/form?id=" + config.getId() : 
                    "redirect:/publish/form";
        }
        
        try {
            log.info("保存发布配置, id={}, name={}", config.getId(), config.getName());
            if (config.getId() != null) {
                publishConfigService.update(config.getId(), config);
            } else {
                publishConfigService.save(config);
            }
            redirectAttributes.addFlashAttribute("success", "保存成功");
            return "redirect:/publish/list";
        } catch (Exception e) {
            log.error("保存发布配置失败", e);
            redirectAttributes.addFlashAttribute("error", "保存失败: " + e.getMessage());
            return config.getId() != null ? 
                    "redirect:/publish/form?id=" + config.getId() : 
                    "redirect:/publish/form";
        }
    }

    /**
     * 删除发布配置
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("删除发布配置, id={}", id);
        try {
            // 先停止服务
            publishService.stopService(id);
            publishConfigService.delete(id);
            redirectAttributes.addFlashAttribute("success", "删除成功");
        } catch (Exception e) {
            log.error("删除发布配置失败, id={}", id, e);
            redirectAttributes.addFlashAttribute("error", "删除失败: " + e.getMessage());
        }
        return "redirect:/publish/list";
    }

    // ============ API 接口 ============

    /**
     * 启动发布服务
     */
    @PostMapping("/api/start/{id}")
    @ResponseBody
    public ApiResponse<String> startService(@PathVariable Long id) {
        log.info("启动发布服务, id={}", id);
        try {
            publishService.startService(id);
            return ApiResponse.success("服务已启动");
        } catch (Exception e) {
            log.error("启动发布服务失败, id={}", id, e);
            return ApiResponse.error("启动失败: " + e.getMessage());
        }
    }

    /**
     * 停止发布服务
     */
    @PostMapping("/api/stop/{id}")
    @ResponseBody
    public ApiResponse<String> stopService(@PathVariable Long id) {
        log.info("停止发布服务, id={}", id);
        try {
            publishService.stopService(id);
            return ApiResponse.success("服务已停止");
        } catch (Exception e) {
            log.error("停止发布服务失败, id={}", id, e);
            return ApiResponse.error("停止失败: " + e.getMessage());
        }
    }

    /**
     * 重启发布服务
     */
    @PostMapping("/api/restart/{id}")
    @ResponseBody
    public ApiResponse<String> restartService(@PathVariable Long id) {
        log.info("重启发布服务, id={}", id);
        try {
            publishService.restartService(id);
            return ApiResponse.success("服务已重启");
        } catch (Exception e) {
            log.error("重启发布服务失败, id={}", id, e);
            return ApiResponse.error("重启失败: " + e.getMessage());
        }
    }

    /**
     * 检查端口可用性
     */
    @GetMapping("/api/checkPort")
    @ResponseBody
    public ApiResponse<Map<String, Object>> checkPort(@RequestParam int port, 
                                                       @RequestParam(required = false) Long excludeId) {
        Map<String, Object> result = new HashMap<>();
        boolean available = publishConfigService.isPortAvailable(port, excludeId);
        result.put("available", available);
        result.put("port", port);
        if (!available) {
            result.put("message", "端口 " + port + " 已被占用");
        }
        return ApiResponse.success(result);
    }

    /**
     * 获取可用端口
     */
    @GetMapping("/api/allocatePort")
    @ResponseBody
    public ApiResponse<Map<String, Object>> allocatePort() {
        Map<String, Object> result = new HashMap<>();
        int port = publishConfigService.allocatePort();
        result.put("port", port);
        return ApiResponse.success(result);
    }

    /**
     * 获取发布状态
     */
    @GetMapping("/api/status/{id}")
    @ResponseBody
    public ApiResponse<Map<String, Object>> getStatus(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        PublishConfig config = publishConfigService.getById(id).orElse(null);
        if (config == null) {
            return ApiResponse.error("发布配置不存在");
        }
        result.put("id", config.getId());
        result.put("status", config.getStatus());
        result.put("running", publishService.isRunning(id));
        result.put("port", config.getPort());
        result.put("lastStartTime", config.getLastStartTime());
        result.put("lastError", config.getLastError());
        return ApiResponse.success(result);
    }

    /**
     * 执行已发布的服务（外部调用入口）
     * POST /publish/execute/{id}
     */
    @PostMapping(value = "/execute/{id}", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ApiResponse<Map<String, Object>> execute(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> params) {
        log.info("收到发布服务调用, id={}, params={}", id, params);
        try {
            Map<String, Object> result = publishService.execute(id, params != null ? params : new HashMap<>());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("发布服务调用失败, id={}", id, e);
            return ApiResponse.error(e.getMessage());
        }
    }
}
