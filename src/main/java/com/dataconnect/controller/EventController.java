package com.dataconnect.controller;

import com.dataconnect.dto.ApiResponse;
import com.dataconnect.entity.EventDefinition;
import com.dataconnect.service.EventDefinitionService;
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
 * 事件管理控制器
 */
@Controller
@RequestMapping("/event")
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);

    @Autowired
    private EventDefinitionService eventDefinitionService;

    /**
     * 事件管理列表页
     */
    @GetMapping("/list")
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String category,
                       Model model) {
        log.info("访问事件管理列表页, keyword={}, category={}", keyword, category);

        List<EventDefinition> list = eventDefinitionService.listAll();
        // 分类过滤
        if (category != null && !category.isEmpty()) {
            list.removeIf(e -> !category.equals(e.getCategory()));
        }
        // 关键词搜索
        if (keyword != null && !keyword.isEmpty()) {
            String kw = keyword.toLowerCase();
            list.removeIf(e -> !e.getName().toLowerCase().contains(kw)
                    && !e.getCode().toLowerCase().contains(kw)
                    && (e.getDescription() == null || !e.getDescription().toLowerCase().contains(kw)));
        }

        // 获取所有分类
        java.util.Set<String> categories = new java.util.LinkedHashSet<>();
        for (EventDefinition e : list) {
            if (e.getCategory() != null) categories.add(e.getCategory());
        }

        model.addAttribute("activeMenu", "event");
        model.addAttribute("pageTitle", "事件管理");
        model.addAttribute("list", list);
        model.addAttribute("categories", categories);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategory", category);
        return "event/list";
    }

    /**
     * 事件编辑页（新建/编辑）
     */
    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id, Model model) {
        log.info("访问事件编辑页, id={}", id);

        EventDefinition event = id != null ?
                eventDefinitionService.getById(id).orElse(new EventDefinition()) :
                new EventDefinition();

        model.addAttribute("activeMenu", "event");
        model.addAttribute("pageTitle", id != null ? "编辑事件" : "新建事件");
        model.addAttribute("event", event);
        return "event/form";
    }

    /**
     * 保存事件
     */
    @PostMapping("/save")
    public String save(EventDefinition event, RedirectAttributes redirectAttributes) {
        if (event.getName() == null || event.getName().trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "事件名称不能为空");
            return "redirect:/event/form";
        }
        if (event.getCode() == null || event.getCode().trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "事件编码不能为空");
            return "redirect:/event/form";
        }

        try {
            if (event.getId() != null) {
                eventDefinitionService.update(event.getId(), event);
            } else {
                eventDefinitionService.save(event);
            }
            redirectAttributes.addFlashAttribute("success", "保存成功");
            return "redirect:/event/list";
        } catch (Exception e) {
            log.error("保存事件失败", e);
            redirectAttributes.addFlashAttribute("error", "保存失败: " + e.getMessage());
            return "redirect:/event/form";
        }
    }

    /**
     * 删除事件
     */
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("删除事件, id={}", id);
        try {
            eventDefinitionService.delete(id);
            redirectAttributes.addFlashAttribute("success", "删除成功");
        } catch (Exception e) {
            log.error("删除事件失败, id={}", id, e);
            redirectAttributes.addFlashAttribute("error", "删除失败: " + e.getMessage());
        }
        return "redirect:/event/list";
    }

    // ============ API 接口 ============

    /**
     * 获取事件列表（供模板步骤使用）
     */
    @GetMapping("/api/list")
    @ResponseBody
    public ApiResponse<List<Map<String, Object>>> apiList() {
        try {
            List<Map<String, Object>> list = eventDefinitionService.listForApi();
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("获取事件列表失败", e);
            return ApiResponse.error("获取事件列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取事件详情
     */
    @GetMapping("/api/detail/{id}")
    @ResponseBody
    public ApiResponse<EventDefinition> getDetail(@PathVariable Long id) {
        return eventDefinitionService.getById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error("事件不存在"));
    }

    /**
     * 保存事件（API）
     */
    @PostMapping("/api/save")
    @ResponseBody
    public ApiResponse<EventDefinition> saveApi(@RequestBody EventDefinition event) {
        try {
            EventDefinition saved;
            if (event.getId() != null) {
                saved = eventDefinitionService.update(event.getId(), event);
            } else {
                saved = eventDefinitionService.save(event);
            }
            return ApiResponse.success(saved);
        } catch (Exception e) {
            log.error("保存事件失败", e);
            return ApiResponse.error("保存失败: " + e.getMessage());
        }
    }
}
