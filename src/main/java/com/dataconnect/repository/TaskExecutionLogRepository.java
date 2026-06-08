package com.dataconnect.repository;

import com.dataconnect.entity.TaskExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskExecutionLogRepository extends JpaRepository<TaskExecutionLog, Long> {

    List<TaskExecutionLog> findByTaskIdOrderByCreateTimeDesc(Long taskId);

    TaskExecutionLog findTopByTaskIdOrderByCreateTimeDesc(Long taskId);
}
