package com.dataconnect.service;

import com.dataconnect.entity.MappingTemplate;
import com.dataconnect.repository.MappingTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MappingTemplateService {

    private static final Logger log = LoggerFactory.getLogger(MappingTemplateService.class);

    @Autowired
    private MappingTemplateRepository mappingTemplateRepository;

    @Autowired
    private PostmanJsonParser postmanJsonParser;

    public List<MappingTemplate> listAll() {
        return mappingTemplateRepository.findAll();
    }

    public List<MappingTemplate> search(String keyword) {
        return mappingTemplateRepository.findByNameContaining(keyword);
    }

    public Optional<MappingTemplate> getById(Long id) {
        return mappingTemplateRepository.findById(id);
    }

    public MappingTemplate save(MappingTemplate template) {
        log.info("保存对接模板, name={}", template.getName());
        MappingTemplate saved = mappingTemplateRepository.save(template);
        log.info("对接模板已保存, id={}", saved.getId());
        return saved;
    }

    public MappingTemplate update(Long id, MappingTemplate updated) {
        log.info("更新对接模板, id={}, name={}", id, updated.getName());
        MappingTemplate existing = mappingTemplateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("对接模板不存在: " + id));
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setDsConfigId(updated.getDsConfigId());
        existing.setColumnConfigId(updated.getColumnConfigId());
        existing.setPushColumnConfigId(updated.getPushColumnConfigId());
        existing.setMappings(updated.getMappings());
        existing.setPostmanJson(updated.getPostmanJson());
        MappingTemplate saved = mappingTemplateRepository.save(existing);
        log.info("对接模板已更新, id={}", saved.getId());
        return saved;
    }

    public void delete(Long id) {
        log.info("删除对接模板, id={}", id);
        mappingTemplateRepository.deleteById(id);
        log.info("对接模板已删除, id={}", id);
    }

    public List<Map<String, String>> parsePostmanJson(String postmanJson) {
        return postmanJsonParser.parse(postmanJson);
    }
}
