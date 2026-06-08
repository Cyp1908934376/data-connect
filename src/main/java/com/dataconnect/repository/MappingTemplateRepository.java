package com.dataconnect.repository;

import com.dataconnect.entity.MappingTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MappingTemplateRepository extends JpaRepository<MappingTemplate, Long> {
    List<MappingTemplate> findByNameContaining(String keyword);
}
