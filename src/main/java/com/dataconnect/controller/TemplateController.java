package com.dataconnect.controller;

import com.dataconnect.dto.ApiResponse;
import com.dataconnect.entity.TemplateCategory;
import com.dataconnect.entity.TemplateEntity;
import com.dataconnect.entity.TemplateSnippet;
import com.dataconnect.entity.TemplateVersion;
import com.dataconnect.service.TemplateService;
import com.dataconnect.service.TemplateSnippetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/template")
public class TemplateController {

    private static final Logger log = LoggerFactory.getLogger(TemplateController.class);

    @Autowired
    private TemplateService templateService;

    @Autowired
    private TemplateSnippetService snippetService;

    @GetMapping("/list")
    public String list(@RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) String keyword, Model model) {
        log.info("访问模板列表页, categoryId={}, keyword={}", categoryId, keyword);
        List<TemplateEntity> templates;
        if (keyword != null && !keyword.isEmpty()) {
            templates = templateService.search(keyword);
        } else if (categoryId != null && categoryId > 0) {
            templates = templateService.listByCategory(categoryId);
        } else {
            templates = templateService.listAll();
        }
        model.addAttribute("activeMenu", "template");
        model.addAttribute("pageTitle", "模板管理");
        model.addAttribute("templates", templates);
        model.addAttribute("categories", templateService.getAllCategories());
        model.addAttribute("categoryTree", templateService.buildCategoryTree());
        model.addAttribute("currentCategoryId", categoryId);
        return "template/list";
    }

    private static final String DEFAULT_TEMPLATE_CONTENT =
            "// ============================================\n" +
            "// Groovy 模板脚本\n" +
            "// 可用变量:\n" +
            "//   input  - Map<String, Object>, 当前数据行\n" +
            "//   params - Map<String, Object>, 模板参数 (来自流程配置)\n" +
            "//   out    - Map<String, Object>, 输出结果 (优先读取)\n" +
            "// 返回值: 优先取 out, 其次取 return 的 Map/JSON 字符串\n" +
            "// ============================================\n" +
            "\n" +
            "// 示例: 字段映射与转换\n" +
            "// out['target_id'] = input['source_id']\n" +
            "// out['full_name'] = input['first_name'] + ' ' + input['last_name']\n" +
            "// out['amount'] = (input['amount'] as double).round(2)\n" +
            "\n" +
            "// TODO: 在此编写处理逻辑\n";

    @GetMapping("/editor")
    public String editor(@RequestParam(required = false) Long id, Model model) {
        log.info("访问模板编辑器, id={}", id);
        TemplateEntity template;
        if (id != null) {
            template = templateService.getById(id).orElse(new TemplateEntity());
        } else {
            template = new TemplateEntity();
            template.setContent(DEFAULT_TEMPLATE_CONTENT);
            template.setType("CUSTOM");
        }
        model.addAttribute("activeMenu", "template");
        model.addAttribute("pageTitle", id != null ? "编辑模板" : "新增模板");
        model.addAttribute("template", template);
        model.addAttribute("categories", templateService.getAllCategories());
        if (id != null) {
            model.addAttribute("versions", templateService.getVersions(id));
        }
        return "template/editor";
    }

    @PostMapping("/save")
    public String save(TemplateEntity template) {
        log.info("保存模板, id={}, name={}, type={}", template.getId(), template.getName(), template.getType());
        try {
            if (template.getId() != null) {
                templateService.update(template.getId(), template);
            } else {
                templateService.save(template);
            }
            log.info("模板保存成功, id={}", template.getId());
        } catch (Exception e) {
            log.error("保存模板失败, id={}", template.getId(), e);
            throw e;
        }
        return "redirect:/template/list";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        log.info("软删除模板, id={}", id);
        templateService.softDelete(id);
        return "redirect:/template/list";
    }

    @PostMapping("/hardDelete/{id}")
    public String hardDelete(@PathVariable Long id) {
        log.info("硬删除模板, id={}", id);
        templateService.hardDelete(id);
        return "redirect:/template/list";
    }

    @PostMapping("/rollback/{templateId}/{versionId}")
    public String rollback(@PathVariable Long templateId, @PathVariable Long versionId) {
        log.info("回滚模板版本, templateId={}, versionId={}", templateId, versionId);
        templateService.rollback(templateId, versionId);
        return "redirect:/template/editor?id=" + templateId;
    }

    // === REST API ===
    @PostMapping("/api/saveCategory")
    @ResponseBody
    public ApiResponse<TemplateCategory> saveCategory(TemplateCategory category) {
        log.info("保存模板分类, id={}, name={}", category.getId(), category.getName());
        return ApiResponse.success(templateService.saveCategory(category));
    }

    @PostMapping("/api/deleteCategory/{id}")
    @ResponseBody
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        log.info("删除模板分类, id={}", id);
        templateService.deleteCategory(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/categoryTree")
    @ResponseBody
    public ApiResponse<List<Map<String, Object>>> categoryTree() {
        log.debug("查询模板分类树");
        return ApiResponse.success(templateService.buildCategoryTree());
    }

    @GetMapping("/api/versions/{templateId}")
    @ResponseBody
    public ApiResponse<List<TemplateVersion>> versions(@PathVariable Long templateId) {
        log.debug("查询模板版本列表, templateId={}", templateId);
        return ApiResponse.success(templateService.getVersions(templateId));
    }

    // === 代码片段管理 API ===

    @GetMapping("/api/snippets")
    @ResponseBody
    public ApiResponse<List<Map<String, Object>>> listSnippets() {
        return ApiResponse.success(snippetService.listGrouped());
    }

    @PostMapping("/api/saveSnippet")
    @ResponseBody
    public ApiResponse<TemplateSnippet> saveSnippet(TemplateSnippet snippet) {
        log.info("保存模板片段, id={}, name={}", snippet.getId(), snippet.getName());
        try {
            if (snippet.getId() != null) {
                return ApiResponse.success(snippetService.update(snippet.getId(), snippet));
            } else {
                return ApiResponse.success(snippetService.save(snippet));
            }
        } catch (Exception e) {
            log.error("保存模板片段失败, id={}", snippet.getId(), e);
            throw e;
        }
    }

    @PostMapping("/api/deleteSnippet/{id}")
    @ResponseBody
    public ApiResponse<Void> deleteSnippet(@PathVariable Long id) {
        log.info("删除模板片段, id={}", id);
        snippetService.delete(id);
        return ApiResponse.success(null);
    }
}
