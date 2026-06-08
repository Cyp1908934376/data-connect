package com.dataconnect.service;

import com.dataconnect.entity.ColumnConfig;
import com.dataconnect.repository.ColumnConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ColumnConfigService {

    private static final Logger log = LoggerFactory.getLogger(ColumnConfigService.class);

    @Autowired
    private ColumnConfigRepository columnConfigRepository;

    public List<ColumnConfig> listAll() {
        return columnConfigRepository.findAll();
    }

    public List<ColumnConfig> search(String keyword) {
        return columnConfigRepository.findByNameContaining(keyword);
    }

    public List<ColumnConfig> listByType(String columnType) {
        return columnConfigRepository.findByColumnType(columnType);
    }

    public Optional<ColumnConfig> getById(Long id) {
        return columnConfigRepository.findById(id);
    }

    public ColumnConfig save(ColumnConfig config) {
        log.info("保存列配置, name={}, type={}", config.getName(), config.getColumnType());
        ColumnConfig saved = columnConfigRepository.save(config);
        log.info("列配置已保存, id={}", saved.getId());
        return saved;
    }

    public ColumnConfig update(Long id, ColumnConfig updated) {
        log.info("更新列配置, id={}, name={}", id, updated.getName());
        ColumnConfig existing = columnConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("列配置不存在: " + id));
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setColumnType(updated.getColumnType());
        existing.setColumnsJson(updated.getColumnsJson());
        ColumnConfig saved = columnConfigRepository.save(existing);
        log.info("列配置已更新, id={}", saved.getId());
        return saved;
    }

    public void delete(Long id) {
        log.info("删除列配置, id={}", id);
        columnConfigRepository.deleteById(id);
        log.info("列配置已删除, id={}", id);
    }
}
