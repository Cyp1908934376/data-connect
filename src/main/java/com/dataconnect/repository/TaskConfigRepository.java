package com.dataconnect.repository;

import com.dataconnect.entity.TaskConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskConfigRepository extends JpaRepository<TaskConfig, Long> {

    List<TaskConfig> findByStatus(String status);

    Optional<TaskConfig> findFirstByVisualTemplateIdAndTaskType(Long visualTemplateId, String taskType);

    List<TaskConfig> findAllByOrderByUpdateTimeDesc();
}
