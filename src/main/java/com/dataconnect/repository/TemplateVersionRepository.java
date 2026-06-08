package com.dataconnect.repository;

import com.dataconnect.entity.TemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, Long> {

    List<TemplateVersion> findByTemplateIdOrderByVersionDesc(Long templateId);
}
