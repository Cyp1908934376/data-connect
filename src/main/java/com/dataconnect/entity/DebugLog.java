package com.dataconnect.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "debug_log")
public class DebugLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ds_config_id")
    private Long dsConfigId;

    @Column(name = "operation_type", length = 50)
    private String operationType;

    @Column(name = "config_snapshot", columnDefinition = "TEXT")
    private String configSnapshot;

    @Column(name = "result_status", length = 20)
    private String resultStatus;

    @Column(name = "result_snapshot", columnDefinition = "TEXT")
    private String resultSnapshot;

    @Column
    private Long duration;  // 毫秒

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDsConfigId() { return dsConfigId; }
    public void setDsConfigId(Long dsConfigId) { this.dsConfigId = dsConfigId; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getConfigSnapshot() { return configSnapshot; }
    public void setConfigSnapshot(String configSnapshot) { this.configSnapshot = configSnapshot; }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
    public String getResultSnapshot() { return resultSnapshot; }
    public void setResultSnapshot(String resultSnapshot) { this.resultSnapshot = resultSnapshot; }
    public Long getDuration() { return duration; }
    public void setDuration(Long duration) { this.duration = duration; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
