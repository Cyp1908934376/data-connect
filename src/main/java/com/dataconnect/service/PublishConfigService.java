package com.dataconnect.service;

import com.dataconnect.entity.PublishConfig;
import com.dataconnect.repository.PublishConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 发布配置服务
 * 管理发布配置的CRUD操作
 */
@Service
public class PublishConfigService {

    private static final Logger log = LoggerFactory.getLogger(PublishConfigService.class);

    @Autowired
    private PublishConfigRepository publishConfigRepository;

    @Autowired
    private PortManager portManager;

    /**
     * 获取所有发布配置
     */
    public List<PublishConfig> listAll() {
        return publishConfigRepository.findAll();
    }

    /**
     * 根据ID获取发布配置
     */
    public Optional<PublishConfig> getById(Long id) {
        return publishConfigRepository.findById(id);
    }

    /**
     * 根据状态获取发布配置
     */
    public List<PublishConfig> listByStatus(String status) {
        return publishConfigRepository.findByStatus(status);
    }

    /**
     * 保存发布配置
     */
    public PublishConfig save(PublishConfig config) {
        log.info("保存发布配置, name={}", config.getName());
        
        // 如果没有分配端口，自动分配（端口仅作为标识，实际使用主应用端口）
        if (config.getPort() == null || config.getPort() == 0) {
            config.setPort(portManager.allocatePort());
        }
        
        // 设置默认值
        if (config.getFlowConfigId() == null) {
            config.setFlowConfigId(0L);
        }
        if (config.getVisualTemplateId() == null) {
            config.setVisualTemplateId(0L);
        }
        if (config.getStatus() == null) {
            config.setStatus("STOPPED");
        }
        if (config.getApiPath() == null) {
            config.setApiPath("/api/data");
        }
        if (config.getAuthType() == null) {
            config.setAuthType("NONE");
        }
        
        PublishConfig saved = publishConfigRepository.save(config);
        log.info("发布配置已保存, id={}, port={}", saved.getId(), saved.getPort());
        return saved;
    }

    /**
     * 更新发布配置
     */
    public PublishConfig update(Long id, PublishConfig updated) {
        PublishConfig existing = publishConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("发布配置不存在: " + id));
        
        // 端口更新（仅作为标识，不验证系统可用性）
        if (updated.getPort() != null) {
            existing.setPort(updated.getPort());
        }
        
        // 更新字段
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setFlowConfigId(updated.getFlowConfigId());
        existing.setVisualTemplateId(updated.getVisualTemplateId());
        existing.setApiPath(updated.getApiPath());
        existing.setAuthType(updated.getAuthType());
        existing.setAuthConfig(updated.getAuthConfig());
        existing.setRateLimit(updated.getRateLimit());
        existing.setCacheTtl(updated.getCacheTtl());
        
        PublishConfig saved = publishConfigRepository.save(existing);
        log.info("发布配置已更新, id={}", saved.getId());
        return saved;
    }

    /**
     * 删除发布配置
     */
    public void delete(Long id) {
        log.info("删除发布配置, id={}", id);
        publishConfigRepository.deleteById(id);
    }

    /**
     * 更新发布状态
     */
    public void updateStatus(Long id, String status) {
        PublishConfig config = publishConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("发布配置不存在: " + id));
        config.setStatus(status);
        if ("RUNNING".equals(status)) {
            config.setLastStartTime(LocalDateTime.now());
            config.setLastError(null);
        }
        publishConfigRepository.save(config);
    }

    /**
     * 更新错误信息
     */
    public void updateError(Long id, String error) {
        PublishConfig config = publishConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("发布配置不存在: " + id));
        config.setStatus("ERROR");
        config.setLastError(error);
        publishConfigRepository.save(config);
    }

    /**
     * 检查端口是否可用
     */
    public boolean isPortAvailable(int port, Long excludeId) {
        return portManager.isPortAvailable(port, excludeId);
    }

    /**
     * 分配可用端口
     */
    public int allocatePort() {
        return portManager.allocatePort();
    }
}
