package com.dataconnect.service;

import com.dataconnect.entity.VisualTemplate;
import com.dataconnect.repository.VisualTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 可视化模板服务
 */
@Service
public class VisualTemplateService {

    private static final Logger log = LoggerFactory.getLogger(VisualTemplateService.class);

    @Autowired
    private VisualTemplateRepository visualTemplateRepository;

    @Autowired
    @Lazy
    private VisualTemplateScheduleService visualTemplateScheduleService;

    /**
     * 获取所有模板（未删除）
     */
    public List<VisualTemplate> listAll() {
        return visualTemplateRepository.findByIsDeletedOrderByUpdateTimeDesc(0);
    }

    /**
     * 根据分类获取模板
     */
    public List<VisualTemplate> listByCategory(Long categoryId) {
        return visualTemplateRepository.findByCategoryIdAndIsDeletedOrderByUpdateTimeDesc(categoryId, 0);
    }

    /**
     * 搜索模板
     */
    public List<VisualTemplate> search(String keyword) {
        return visualTemplateRepository.findByNameContainingAndIsDeletedOrderByUpdateTimeDesc(keyword, 0);
    }

    /**
     * 根据ID获取模板
     */
    public Optional<VisualTemplate> getById(Long id) {
        return visualTemplateRepository.findById(id);
    }

    /**
     * 保存模板
     */
    public VisualTemplate save(VisualTemplate template) {
        log.info("保存可视化模板, name={}", template.getName());
        // 新建时不允许客户端伪造系统内置标识
        if (template.getId() == null) {
            template.setBuiltinCode(null);
        }
        
        if (template.getCanvasConfig() == null) {
            template.setCanvasConfig("{}");
        }
        if (template.getInputParams() == null) {
            template.setInputParams("[]");
        }
        if (template.getOutputParams() == null) {
            template.setOutputParams("[]");
        }
        
        VisualTemplate saved = visualTemplateRepository.save(template);
        log.info("可视化模板已保存, id={}", saved.getId());
        visualTemplateScheduleService.sync(saved);
        return saved;
    }

    /**
     * 更新模板
     */
    public VisualTemplate update(Long id, VisualTemplate updated) {
        VisualTemplate existing = visualTemplateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("可视化模板不存在: " + id));

        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        if (updated.getCategoryId() != null) {
            existing.setCategoryId(updated.getCategoryId());
        }
        if (!existing.isBuiltin() && updated.getEventType() != null) {
            existing.setEventType(updated.getEventType());
        }
        if (updated.getEventConfig() != null) {
            existing.setEventConfig(updated.getEventConfig());
        }
        if (updated.getCallTemplates() != null) {
            existing.setCallTemplates(updated.getCallTemplates());
        }
        if (updated.getCanvasConfig() != null) {
            existing.setCanvasConfig(updated.getCanvasConfig());
        }
        if (updated.getInputParams() != null) {
            existing.setInputParams(updated.getInputParams());
        }
        if (updated.getOutputParams() != null) {
            existing.setOutputParams(updated.getOutputParams());
        }
        existing.setVersion(existing.getVersion() + 1);

        VisualTemplate saved = visualTemplateRepository.save(existing);
        log.info("可视化模板已更新, id={}, version={}", saved.getId(), saved.getVersion());
        visualTemplateScheduleService.sync(saved);
        return saved;
    }

    /**
     * 软删除模板
     */
    public void softDelete(Long id) {
        VisualTemplate template = visualTemplateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("可视化模板不存在: " + id));
        if (template.isBuiltin()) {
            throw new RuntimeException("系统内置模板不可删除");
        }
        template.setIsDeleted(1);
        visualTemplateRepository.save(template);
        visualTemplateScheduleService.stop(id);
        log.info("可视化模板已删除, id={}", id);
    }

    /**
     * 硬删除模板
     */
    public void hardDelete(Long id) {
        VisualTemplate template = visualTemplateRepository.findById(id).orElse(null);
        if (template != null && template.isBuiltin()) {
            throw new RuntimeException("系统内置模板不可删除");
        }
        visualTemplateScheduleService.stop(id);
        visualTemplateRepository.deleteById(id);
        log.info("可视化模板已硬删除, id={}", id);
    }
}
