package com.dataconnect.service;

import com.dataconnect.entity.TemplateSnippet;
import com.dataconnect.repository.TemplateSnippetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TemplateSnippetService {

    private static final Logger log = LoggerFactory.getLogger(TemplateSnippetService.class);

    @Autowired
    private TemplateSnippetRepository snippetRepository;

    public List<TemplateSnippet> listAll() {
        return snippetRepository.findAllByOrderByGroupNameAscSortOrderAsc();
    }

    /**
     * 返回按分组聚合的列表，供编辑器前端直接使用。
     * 格式: [{group, items: [{name, description, code}]}]
     */
    public List<Map<String, Object>> listGrouped() {
        List<TemplateSnippet> all = listAll();
        Map<String, List<Map<String, String>>> grouped = new LinkedHashMap<>();
        for (TemplateSnippet s : all) {
            String group = s.getGroupName() != null && !s.getGroupName().isEmpty() ? s.getGroupName() : "默认";
            grouped.computeIfAbsent(group, k -> new ArrayList<>()).add(toItemMap(s));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, String>>> entry : grouped.entrySet()) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("title", entry.getKey());
            g.put("items", entry.getValue());
            result.add(g);
        }
        return result;
    }

    private Map<String, String> toItemMap(TemplateSnippet s) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("id", String.valueOf(s.getId()));
        item.put("label", s.getName());
        item.put("desc", s.getDescription() != null ? s.getDescription() : "");
        item.put("code", s.getCode());
        return item;
    }

    public Optional<TemplateSnippet> getById(Long id) {
        return snippetRepository.findById(id);
    }

    public TemplateSnippet save(TemplateSnippet snippet) {
        log.info("保存模板片段, id={}, name={}", snippet.getId(), snippet.getName());
        return snippetRepository.save(snippet);
    }

    public TemplateSnippet update(Long id, TemplateSnippet updated) {
        log.info("更新模板片段, id={}, name={}", id, updated.getName());
        TemplateSnippet existing = snippetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模板片段不存在: " + id));
        existing.setName(updated.getName());
        existing.setGroupName(updated.getGroupName());
        existing.setDescription(updated.getDescription());
        existing.setCode(updated.getCode());
        existing.setSortOrder(updated.getSortOrder());
        return snippetRepository.save(existing);
    }

    public void delete(Long id) {
        log.info("删除模板片段, id={}", id);
        snippetRepository.deleteById(id);
    }
}
