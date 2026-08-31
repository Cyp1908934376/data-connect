package com.dataconnect.repository;

import com.dataconnect.entity.ComponentDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComponentDefinitionRepository extends JpaRepository<ComponentDefinition, Long> {
    List<ComponentDefinition> findByCategoryOrderBySortOrder(String category);
    List<ComponentDefinition> findByEnabledOrderBySortOrder(Integer enabled);
    List<ComponentDefinition> findByCategoryAndEnabledOrderBySortOrder(String category, Integer enabled);
}
