package com.dataconnect.repository;

import com.dataconnect.entity.TemplateCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemplateCategoryRepository extends JpaRepository<TemplateCategory, Long> {

    List<TemplateCategory> findByParentIdOrderBySortOrder(Long parentId);
}
