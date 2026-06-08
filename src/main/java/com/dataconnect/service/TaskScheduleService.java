package com.dataconnect.service;

import com.dataconnect.entity.TaskConfig;
import com.dataconnect.entity.TaskExecutionLog;
import com.dataconnect.repository.TaskConfigRepository;
import com.dataconnect.repository.TaskExecutionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class TaskScheduleService {

    private static final Logger log = LoggerFactory.getLogger(TaskScheduleService.class);

    @Autowired
    private TaskConfigRepository taskConfigRepository;

    @Autowired
    private TaskExecutionLogRepository executionLogRepository;

    @Autowired
    private FlowExecutionService flowExecutionService;

    private final ThreadPoolTaskScheduler scheduler;
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public TaskScheduleService() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("task-exec-");
        scheduler.initialize();
    }

    public void startTask(Long taskId) {
        TaskConfig task = taskConfigRepository.findById(taskId).orElse(null);
        if (task == null || task.getCronExpr() == null || task.getCronExpr().isEmpty()) return;

        ScheduledFuture<?> existing = scheduledTasks.get(taskId);
        if (existing != null) {
            existing.cancel(false);
        }

        task.setStatus("RUNNING");
        taskConfigRepository.save(task);

        ScheduledFuture<?> future = scheduler.schedule(
                () -> executeTask(taskId),
                new CronTrigger(task.getCronExpr())
        );
        scheduledTasks.put(taskId, future);
        log.info("Task started: {} (id={}, cron={})", task.getName(), taskId, task.getCronExpr());
    }

    public void pauseTask(Long taskId) {
        log.info("暂停任务, id={}", taskId);
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) future.cancel(false);
        TaskConfig task = taskConfigRepository.findById(taskId).orElse(null);
        if (task != null) {
            task.setStatus("PAUSED");
            taskConfigRepository.save(task);
            log.info("任务已暂停, id={}, name={}", taskId, task.getName());
        }
    }

    public void stopTask(Long taskId) {
        log.info("停止任务, id={}", taskId);
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) future.cancel(true);
        TaskConfig task = taskConfigRepository.findById(taskId).orElse(null);
        if (task != null) {
            task.setStatus("STOPPED");
            taskConfigRepository.save(task);
            log.info("任务已停止, id={}, name={}", taskId, task.getName());
        }
    }

    public void executeOnce(Long taskId) {
        log.info("手动执行任务一次, id={}", taskId);
        scheduler.execute(() -> executeTask(taskId));
    }

    private void executeTask(Long taskId) {
        TaskConfig task = taskConfigRepository.findById(taskId).orElse(null);
        if (task == null) return;

        TaskExecutionLog execLog = new TaskExecutionLog();
        execLog.setTaskId(taskId);
        execLog.setStartTime(LocalDateTime.now());
        execLog.setStatus("RUNNING");
        execLog = executionLogRepository.save(execLog);

        try {
            log.info("Executing task: {}", task.getName());
            Map<String, Object> result = flowExecutionService.execute(task.getFlowConfigId());

            execLog.setStatus(Boolean.TRUE.equals(result.get("success")) ? "SUCCESS" : "FAILED");
            execLog.setTotalCount((Integer) result.getOrDefault("totalCount", 0));
            execLog.setSuccessCount((Integer) result.getOrDefault("successCount", 0));
            execLog.setFailCount((Integer) result.getOrDefault("failCount", 0));
            execLog.setEndTime(LocalDateTime.now());

            @SuppressWarnings("unchecked")
            List<String> logs = (List<String>) result.get("logs");
            if (logs != null) {
                execLog.setLogDetail(String.join("\n", logs));
            }
        } catch (Exception e) {
            log.error("Task execution failed: {}", task.getName(), e);
            execLog.setStatus("FAILED");
            execLog.setEndTime(LocalDateTime.now());
            execLog.setLogDetail(e.getMessage());
        }
        executionLogRepository.save(execLog);
        log.info("任务执行结束, taskId={}, name={}, status={}", taskId, task.getName(), execLog.getStatus());
    }

    // === Task CRUD ===
    public List<TaskConfig> listAll() {
        return taskConfigRepository.findAll();
    }

    public Optional<TaskConfig> getById(Long id) {
        return taskConfigRepository.findById(id);
    }

    public TaskConfig save(TaskConfig config) {
        log.info("保存任务, name={}, cron={}", config.getName(), config.getCronExpr());
        TaskConfig saved = taskConfigRepository.save(config);
        log.info("任务已保存, id={}, name={}, status={}", saved.getId(), saved.getName(), saved.getStatus());
        if ("RUNNING".equals(saved.getStatus())) {
            startTask(saved.getId());
        }
        return saved;
    }

    public TaskConfig update(Long id, TaskConfig updated) {
        log.info("更新任务, id={}, name={}, cron={}", id, updated.getName(), updated.getCronExpr());
        TaskConfig existing = taskConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + id));
        existing.setName(updated.getName());
        existing.setFlowConfigId(updated.getFlowConfigId());
        existing.setCronExpr(updated.getCronExpr());
        existing.setRetryTimes(updated.getRetryTimes());
        existing.setRetryInterval(updated.getRetryInterval());
        existing.setTimeout(updated.getTimeout());
        existing.setNotifyUrl(updated.getNotifyUrl());
        String oldStatus = existing.getStatus();
        TaskConfig saved = taskConfigRepository.save(existing);
        log.info("任务已更新, id={}, name={}, oldStatus={}, newStatus={}", saved.getId(), saved.getName(), oldStatus, saved.getStatus());

        if ("RUNNING".equals(saved.getStatus()) && !"RUNNING".equals(oldStatus)) {
            startTask(saved.getId());
        }
        return saved;
    }

    public void delete(Long id) {
        log.info("删除任务, id={}", id);
        stopTask(id);
        executionLogRepository.findByTaskIdOrderByCreateTimeDesc(id)
                .forEach(l -> executionLogRepository.deleteById(l.getId()));
        taskConfigRepository.deleteById(id);
        log.info("任务已删除, id={}", id);
    }

    public List<TaskExecutionLog> getExecutionLogs(Long taskId) {
        return executionLogRepository.findByTaskIdOrderByCreateTimeDesc(taskId);
    }
}
