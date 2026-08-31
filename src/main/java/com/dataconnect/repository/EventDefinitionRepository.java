package com.dataconnect.repository;

import com.dataconnect.entity.EventDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventDefinitionRepository extends JpaRepository<EventDefinition, Long> {
    List<EventDefinition> findByIsEnabledOrderByCategoryAscNameAsc(Integer isEnabled);
    List<EventDefinition> findByCategoryAndIsEnabled(String category, Integer isEnabled);
    Optional<EventDefinition> findByCode(String code);
    List<EventDefinition> findByNameContainingOrDescriptionContaining(String name, String desc);
}
