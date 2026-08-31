package com.dataconnect.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 发布配置实体
 * 将对接流程部署为独立API服务
 */
@Entity
@Table(name = "publish_config")
public class PublishConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "flow_config_id")
    private Long flowConfigId;

    @Column(name = "visual_template_id")
    private Long visualTemplateId;

    @Column(name = "port", nullable = false)
    private Integer port;

    @Column(name = "status", length = 20)
    private String status = "STOPPED";  // RUNNING/STOPPED/ERROR

    @Column(name = "api_path", length = 200)
    private String apiPath = "/api/data";

    @Column(name = "auth_type", length = 20)
    private String authType = "NONE";  // NONE/TOKEN/BASIC

    @Column(name = "auth_config", columnDefinition = "TEXT")
    private String authConfig;

    @Column(name = "rate_limit")
    private Integer rateLimit = 0;

    @Column(name = "cache_ttl")
    private Integer cacheTtl = 0;

    @Column(name = "last_start_time")
    private LocalDateTime lastStartTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) status = "STOPPED";
        if (authType == null) authType = "NONE";
        if (apiPath == null) apiPath = "/api/data";
        if (rateLimit == null) rateLimit = 0;
        if (cacheTtl == null) cacheTtl = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getFlowConfigId() { return flowConfigId; }
    public void setFlowConfigId(Long flowConfigId) { this.flowConfigId = flowConfigId; }
    public Long getVisualTemplateId() { return visualTemplateId; }
    public void setVisualTemplateId(Long visualTemplateId) { this.visualTemplateId = visualTemplateId; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApiPath() { return apiPath; }
    public void setApiPath(String apiPath) { this.apiPath = apiPath; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getAuthConfig() { return authConfig; }
    public void setAuthConfig(String authConfig) { this.authConfig = authConfig; }
    public Integer getRateLimit() { return rateLimit; }
    public void setRateLimit(Integer rateLimit) { this.rateLimit = rateLimit; }
    public Integer getCacheTtl() { return cacheTtl; }
    public void setCacheTtl(Integer cacheTtl) { this.cacheTtl = cacheTtl; }
    public LocalDateTime getLastStartTime() { return lastStartTime; }
    public void setLastStartTime(LocalDateTime lastStartTime) { this.lastStartTime = lastStartTime; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
