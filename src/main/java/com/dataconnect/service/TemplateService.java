package com.dataconnect.service;

import com.dataconnect.entity.TemplateCategory;
import com.dataconnect.entity.TemplateEntity;
import com.dataconnect.entity.TemplateVersion;
import com.dataconnect.repository.TemplateCategoryRepository;
import com.dataconnect.repository.TemplateRepository;
import com.dataconnect.repository.TemplateVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private TemplateCategoryRepository categoryRepository;

    @Autowired
    private TemplateVersionRepository versionRepository;

    // === 模板 CRUD ===
    public List<TemplateEntity> listAll() {
        return templateRepository.findByIsDeleted(0);
    }

    public List<TemplateEntity> listByCategory(Long categoryId) {
        List<Long> ids = collectDescendantIds(categoryId);
        return templateRepository.findByCategoryIdInAndIsDeleted(ids, 0);
    }

    /**
     * 递归收集父分类及其所有子孙分类的 ID
     */
    private List<Long> collectDescendantIds(Long parentId) {
        List<Long> ids = new ArrayList<>();
        ids.add(parentId);
        List<TemplateCategory> children = categoryRepository.findByParentIdOrderBySortOrder(parentId);
        for (TemplateCategory child : children) {
            ids.addAll(collectDescendantIds(child.getId()));
        }
        return ids;
    }

    public List<TemplateEntity> search(String keyword) {
        return templateRepository.findByNameContainingAndIsDeleted(keyword, 0);
    }

    public Optional<TemplateEntity> getById(Long id) {
        return templateRepository.findById(id);
    }

    public TemplateEntity save(TemplateEntity template) {
        log.info("保存模板, name={}, type={}", template.getName(), template.getType());
        TemplateEntity saved = templateRepository.save(template);
        saveVersion(saved);
        log.info("模板已保存, id={}, name={}, version={}", saved.getId(), saved.getName(), saved.getVersion());
        return saved;
    }

    public TemplateEntity update(Long id, TemplateEntity updated) {
        TemplateEntity existing = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模板不存在: " + id));
        existing.setName(updated.getName());
        existing.setCategoryId(updated.getCategoryId());
        existing.setContent(updated.getContent());
        existing.setVariables(updated.getVariables());
        existing.setType(updated.getType());
        existing.setTags(updated.getTags());
        existing.setVersion(existing.getVersion() + 1);
        TemplateEntity saved = templateRepository.save(existing);
        saveVersion(saved);
        log.info("模板已更新, id={}, name={}, version={}", saved.getId(), saved.getName(), saved.getVersion());
        return saved;
    }

    public void softDelete(Long id) {
        log.info("软删除模板, id={}", id);
        TemplateEntity t = templateRepository.findById(id).orElse(null);
        if (t != null) {
            t.setIsDeleted(1);
            templateRepository.save(t);
            log.info("模板已软删除, id={}", id);
        }
    }

    public void hardDelete(Long id) {
        log.info("硬删除模板, id={}", id);
        versionRepository.findByTemplateIdOrderByVersionDesc(id).forEach(v -> versionRepository.deleteById(v.getId()));
        templateRepository.deleteById(id);
        log.info("模板已硬删除, id={}", id);
    }

    // === 版本管理 ===
    public List<TemplateVersion> getVersions(Long templateId) {
        return versionRepository.findByTemplateIdOrderByVersionDesc(templateId);
    }

    public Optional<TemplateVersion> getVersion(Long versionId) {
        return versionRepository.findById(versionId);
    }

    public TemplateEntity rollback(Long templateId, Long versionId) {
        log.info("回滚模板, templateId={}, versionId={}", templateId, versionId);
        TemplateVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new RuntimeException("版本不存在"));
        TemplateEntity template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("模板不存在"));
        template.setContent(version.getContent());
        template.setVersion(template.getVersion() + 1);
        TemplateEntity saved = templateRepository.save(template);
        saveVersion(saved);
        log.info("模板已回滚, id={}, name={}, newVersion={}", saved.getId(), saved.getName(), saved.getVersion());
        return saved;
    }

    private void saveVersion(TemplateEntity template) {
        TemplateVersion v = new TemplateVersion();
        v.setTemplateId(template.getId());
        v.setVersion(template.getVersion());
        v.setContent(template.getContent());
        v.setChangeLog("版本 " + template.getVersion());
        versionRepository.save(v);
    }

    // === 分类管理 ===
    public List<TemplateCategory> listCategories() {
        return categoryRepository.findByParentIdOrderBySortOrder(0L);
    }

    public List<TemplateCategory> listSubCategories(Long parentId) {
        return categoryRepository.findByParentIdOrderBySortOrder(parentId);
    }

    public List<TemplateCategory> getAllCategories() {
        return categoryRepository.findAll();
    }

    public TemplateCategory saveCategory(TemplateCategory category) {
        log.info("保存模板分类, id={}, name={}, parentId={}", category.getId(), category.getName(), category.getParentId());
        return categoryRepository.save(category);
    }

    public void deleteCategory(Long id) {
        log.info("删除模板分类, id={}", id);
        categoryRepository.deleteById(id);
    }

    public List<Map<String, Object>> buildCategoryTree() {
        List<TemplateCategory> all = categoryRepository.findAll();
        return buildTree(all, 0L);
    }

    private List<Map<String, Object>> buildTree(List<TemplateCategory> all, Long parentId) {
        List<Map<String, Object>> tree = new ArrayList<>();
        for (TemplateCategory cat : all) {
            if (parentId.equals(cat.getParentId())) {
                Map<String, Object> node = new java.util.LinkedHashMap<>();
                node.put("id", cat.getId());
                node.put("name", cat.getName());
                node.put("sortOrder", cat.getSortOrder());
                node.put("children", buildTree(all, cat.getId()));
                tree.add(node);
            }
        }
        return tree;
    }
}
