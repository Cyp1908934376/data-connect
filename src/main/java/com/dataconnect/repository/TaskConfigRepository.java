package com.dataconnect.repository;

import com.dataconnect.entity.TaskConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskConfigRepository extends JpaRepository<TaskConfig, Long> {

    List<TaskConfig> findByStatus(String status);

    List<TaskConfig> findByNameContaining(String keyword);
}
