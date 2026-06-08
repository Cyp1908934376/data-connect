package com.dataconnect.repository;

import com.dataconnect.entity.TemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemplateRepository extends JpaRepository<TemplateEntity, Long> {

    List<TemplateEntity> findByCategoryIdAndIsDeleted(Long categoryId, Integer isDeleted);

    List<TemplateEntity> findByCategoryIdInAndIsDeleted(List<Long> categoryIds, Integer isDeleted);

    List<TemplateEntity> findByNameContainingAndIsDeleted(String keyword, Integer isDeleted);

    List<TemplateEntity> findByIsDeleted(Integer isDeleted);
}
