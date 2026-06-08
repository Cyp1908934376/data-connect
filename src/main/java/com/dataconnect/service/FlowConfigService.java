package com.dataconnect.service;

import com.dataconnect.entity.FlowConfig;
import com.dataconnect.repository.FlowConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FlowConfigService {

    private static final Logger log = LoggerFactory.getLogger(FlowConfigService.class);

    @Autowired
    private FlowConfigRepository flowConfigRepository;

    public List<FlowConfig> listAll() {
        return flowConfigRepository.findAll();
    }

    public Optional<FlowConfig> getById(Long id) {
        return flowConfigRepository.findById(id);
    }

    public FlowConfig save(FlowConfig config) {
        log.info("保存流程配置, name={}", config.getName());
        FlowConfig saved = flowConfigRepository.save(config);
        log.info("流程配置已保存, id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    public FlowConfig update(Long id, FlowConfig updated) {
        log.info("更新流程配置, id={}, name={}", id, updated.getName());
        FlowConfig existing = flowConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("流程配置不存在: " + id));
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setInputDsId(updated.getInputDsId());
        existing.setOutputDsId(updated.getOutputDsId());
        existing.setPreTemplateId(updated.getPreTemplateId());
        existing.setMappingTemplateId(updated.getMappingTemplateId());
        existing.setPostTemplateId(updated.getPostTemplateId());
        existing.setTemplateParams(updated.getTemplateParams());
        existing.setPipelineConfig(updated.getPipelineConfig());
        existing.setSyncStrategy(updated.getSyncStrategy());
        existing.setIncrementalColumn(updated.getIncrementalColumn());
        existing.setIncrementalColumnType(updated.getIncrementalColumnType());
        FlowConfig saved = flowConfigRepository.save(existing);
        log.info("流程配置已更新, id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    public void delete(Long id) {
        log.info("删除流程配置, id={}", id);
        flowConfigRepository.deleteById(id);
        log.info("流程配置已删除, id={}", id);
    }
}
