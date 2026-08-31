package com.dataconnect.service;

import com.dataconnect.component.DataPacket;
import com.dataconnect.entity.TaskConfig;
import com.dataconnect.entity.TaskExecutionLog;
import com.dataconnect.repository.TaskConfigRepository;
import com.dataconnect.repository.TaskExecutionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    @Autowired
    @Lazy
    private VisualTemplateExecutionService visualTemplateExecutionService;

    @Autowired
    @Lazy
    private VisualTemplateScheduleService visualTemplateScheduleService;

    private final ThreadPoolTaskScheduler scheduler;
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public TaskScheduleService() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("task-exec-");
        scheduler.initialize();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void restoreRunningTasks() {
        List<TaskConfig> list = taskConfigRepository.findByStatus("RUNNING");
        int n = 0;
        for (TaskConfig task : list) {
            try {
                startTask(task.getId());
                n++;
            } catch (Exception e) {
                log.error("恢复定时任务失败, id={}, name={}", task.getId(), task.getName(), e);
            }
        }
        log.info("定时任务已恢复, 已启动 {} 个", n);
    }

    public void startTask(Long taskId) {
        TaskConfig task = taskConfigRepository.findById(taskId).orElse(null);
        if (task == null || task.getCronExpr() == null || task.getCronExpr().isEmpty()) return;

        ScheduledFuture<?> existing = scheduledTasks.get(taskId);
        if (existing != null) {
            existing.cancel(false);
        }

        String cron = VisualTemplateScheduleService.normalizeCron(task.getCronExpr());
        try {
            CronTrigger trigger = new CronTrigger(cron);
            task.setStatus("RUNNING");
            task.setCronExpr(cron);
            taskConfigRepository.save(task);

            ScheduledFuture<?> future = scheduler.schedule(
                    () -> executeTask(taskId),
                    trigger
            );
            scheduledTasks.put(taskId, future);
            log.info("Task started: {} (id={}, cron={})", task.getName(), taskId, cron);
        } catch (Exception e) {
            log.error("启动任务失败, Cron 无效, id={}, cron={}", taskId, task.getCronExpr(), e);
            throw new RuntimeException("Cron 表达式无效: " + task.getCronExpr());
        }
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
        if (!running.add(taskId)) {
            log.warn("任务上一轮仍在执行，跳过本次, id={}", taskId);
            return;
        }
        TaskConfig task = taskConfigRepository.findById(taskId).orElse(null);
        if (task == null) {
            running.remove(taskId);
            return;
        }

        TaskExecutionLog execLog = new TaskExecutionLog();
        execLog.setTaskId(taskId);
        execLog.setStartTime(LocalDateTime.now());
        execLog.setStatus("RUNNING");
        execLog = executionLogRepository.save(execLog);

        try {
            log.info("Executing task: {} type={}", task.getName(), task.getTaskType());
            if (task.isVisual()) {
                executeVisualTask(task, execLog);
            } else {
                executeFlowTask(task, execLog);
            }
        } catch (Exception e) {
            log.error("Task execution failed: {}", task.getName(), e);
            execLog.setStatus("FAILED");
            execLog.setEndTime(LocalDateTime.now());
            execLog.setLogDetail(e.getMessage());
        } finally {
            running.remove(taskId);
        }
        executionLogRepository.save(execLog);
        log.info("任务执行结束, taskId={}, name={}, status={}", taskId, task.getName(), execLog.getStatus());
    }

    private void executeFlowTask(TaskConfig task, TaskExecutionLog execLog) {
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
    }

    private void executeVisualTask(TaskConfig task, TaskExecutionLog execLog) {
        Long templateId = task.getVisualTemplateId();
        if (templateId == null || templateId <= 0) {
            throw new RuntimeException("可视化模板任务未关联模板");
        }
        DataPacket result = visualTemplateExecutionService.execute(templateId, DataPacket.empty());
        boolean ok = result != null && result.isSuccess();
        int rows = result != null ? result.size() : 0;
        execLog.setStatus(ok ? "SUCCESS" : "FAILED");
        execLog.setTotalCount(rows);
        execLog.setSuccessCount(ok ? rows : 0);
        execLog.setFailCount(ok ? 0 : 1);
        execLog.setEndTime(LocalDateTime.now());
        if (!ok && result != null) {
            execLog.setLogDetail(result.getErrorMessage());
        } else {
            execLog.setLogDetail("可视化模板执行完成, 返回 " + rows + " 行");
        }
    }

    // === Task CRUD ===
    public List<TaskConfig> listAll() {
        return taskConfigRepository.findAllByOrderByUpdateTimeDesc();
    }

    public Optional<TaskConfig> getById(Long id) {
        return taskConfigRepository.findById(id);
    }

    public TaskConfig save(TaskConfig config) {
        log.info("保存任务, name={}, cron={}", config.getName(), config.getCronExpr());
        if (config.getTaskType() == null || config.getTaskType().isEmpty()) {
            config.setTaskType("FLOW");
        }
        if (config.getCronExpr() != null) {
            config.setCronExpr(VisualTemplateScheduleService.normalizeCron(config.getCronExpr()));
        }
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
        if (!existing.isVisual()) {
            existing.setFlowConfigId(updated.getFlowConfigId());
        }
        existing.setCronExpr(VisualTemplateScheduleService.normalizeCron(
                updated.getCronExpr() != null ? updated.getCronExpr() : existing.getCronExpr()));
        existing.setRetryTimes(updated.getRetryTimes());
        existing.setRetryInterval(updated.getRetryInterval());
        existing.setTimeout(updated.getTimeout());
        existing.setNotifyUrl(updated.getNotifyUrl());
        if (updated.getStatus() != null && !updated.getStatus().isEmpty()) {
            existing.setStatus(updated.getStatus());
        }
        TaskConfig saved = taskConfigRepository.save(existing);
        log.info("任务已更新, id={}, name={}, status={}", saved.getId(), saved.getName(), saved.getStatus());

        if (saved.isVisual() && saved.getVisualTemplateId() != null && saved.getVisualTemplateId() > 0) {
            visualTemplateScheduleService.updateCronExpr(saved.getVisualTemplateId(), saved.getCronExpr());
        }

        if ("RUNNING".equals(saved.getStatus())) {
            startTask(saved.getId());
        } else {
            ScheduledFuture<?> future = scheduledTasks.remove(saved.getId());
            if (future != null) {
                future.cancel(false);
            }
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

    /**
     * 从任务管理删除可视化模板任务：同时把模板输入改回手动，避免重启后又出现。
     */
    public void deleteFromUi(Long id) {
        TaskConfig task = taskConfigRepository.findById(id).orElse(null);
        if (task != null && task.isVisual() && task.getVisualTemplateId() != null && task.getVisualTemplateId() > 0) {
            visualTemplateScheduleService.disableCronOnTemplate(task.getVisualTemplateId());
        }
        delete(id);
    }

    public List<TaskExecutionLog> getExecutionLogs(Long taskId) {
        return executionLogRepository.findByTaskIdOrderByCreateTimeDesc(taskId);
    }
}
