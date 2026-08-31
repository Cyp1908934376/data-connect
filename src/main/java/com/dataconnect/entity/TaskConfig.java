package com.dataconnect.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_config")
public class TaskConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "flow_config_id")
    private Long flowConfigId;

    @Column(name = "cron_expr", length = 100)
    private String cronExpr;

    @Column(length = 20)
    private String status;  // RUNNING/PAUSED/STOPPED

    /** FLOW=对接流程任务；VISUAL=可视化模板输入事件定时 */
    @Column(name = "task_type", length = 20)
    private String taskType;

    @Column(name = "visual_template_id")
    private Long visualTemplateId;

    @Column(name = "retry_times")
    private Integer retryTimes;

    @Column(name = "retry_interval")
    private Integer retryInterval;

    @Column
    private Integer timeout;

    @Column(name = "notify_url", length = 500)
    private String notifyUrl;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) status = "STOPPED";
        if (taskType == null || taskType.isEmpty()) taskType = "FLOW";
        if (flowConfigId == null) flowConfigId = 0L;
        if (visualTemplateId == null) visualTemplateId = 0L;
        if (retryTimes == null) retryTimes = 3;
        if (retryInterval == null) retryInterval = 60;
        if (timeout == null) timeout = 3600;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getFlowConfigId() { return flowConfigId; }
    public void setFlowConfigId(Long flowConfigId) { this.flowConfigId = flowConfigId; }
    public String getCronExpr() { return cronExpr; }
    public void setCronExpr(String cronExpr) { this.cronExpr = cronExpr; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public Long getVisualTemplateId() { return visualTemplateId; }
    public void setVisualTemplateId(Long visualTemplateId) { this.visualTemplateId = visualTemplateId; }
    public boolean isVisual() {
        return "VISUAL".equalsIgnoreCase(taskType);
    }
    public Integer getRetryTimes() { return retryTimes; }
    public void setRetryTimes(Integer retryTimes) { this.retryTimes = retryTimes; }
    public Integer getRetryInterval() { return retryInterval; }
    public void setRetryInterval(Integer retryInterval) { this.retryInterval = retryInterval; }
    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
