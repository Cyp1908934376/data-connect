package com.dataconnect.repository;

import com.dataconnect.entity.VisualTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VisualTemplateRepository extends JpaRepository<VisualTemplate, Long> {
    List<VisualTemplate> findByIsDeletedOrderByUpdateTimeDesc(Integer isDeleted);
    List<VisualTemplate> findByCategoryIdAndIsDeletedOrderByUpdateTimeDesc(Long categoryId, Integer isDeleted);
    List<VisualTemplate> findByNameContainingAndIsDeletedOrderByUpdateTimeDesc(String name, Integer isDeleted);
    Optional<VisualTemplate> findFirstByBuiltinCode(String builtinCode);
}
